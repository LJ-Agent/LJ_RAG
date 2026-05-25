"""MemoryWriteService — gRPC service for capturing conversations and ingesting async task results."""
import time
import json

from communication.grpc_server.generated import memory_pb2, memory_pb2_grpc
from engine.distiller import distill
from engine.classifier import validate_no_pii
from engine.deduplicator import deduplicate
from infrastructure.llm.adapter import embed
from infrastructure.milvus.client import insert_fact_vectors
from infrastructure.mysql.client import (
    insert_fact,
    update_fact,
    upsert_user_profile,
    update_profile_counts,
)
from infrastructure.redis.client import set_working_memory
from loguru import logger


class MemoryWriteService(memory_pb2_grpc.MemoryWriteServiceServicer):

    def CaptureConversation(self, request, context):
        """Sync capture: distill → classify → dedup → store.

        For high-throughput scenarios, prefer the async Kafka path.
        """
        user_id = request.user_id
        conversation = request.conversation
        config = request.config

        logger.info(f"CaptureConversation: user={user_id}, session={conversation.session_id}, "
                     f"messages={len(conversation.messages)}")

        # Ensure user profile exists
        upsert_user_profile(user_id)

        # Convert proto messages to dicts
        msgs = [
            {
                "role": m.role,
                "content": m.content,
                "timestamp_ms": m.timestamp_ms,
                "metadata": dict(m.metadata),
            }
            for m in conversation.messages
        ]

        # 1. Distill
        distill_cfg = {
            "importance_threshold": config.importance_threshold if config.importance_threshold > 0 else 0.3,
            "max_facts_per_batch": config.max_facts_per_batch if config.max_facts_per_batch > 0 else 50,
        }
        facts = distill(user_id, conversation.session_id, msgs, distill_cfg)

        if not facts:
            return memory_pb2.CaptureConversationResponse(
                extracted_facts=0,
                status="accepted",
            )

        # 2. PII check
        enable_pii = config.enable_pii_detection if hasattr(config, 'enable_pii_detection') else True
        if enable_pii:
            for fact in facts:
                try:
                    validate_no_pii(fact, enable_detection=True)
                except Exception:
                    logger.warning(f"PII detected in fact, dropping: {fact['content'][:100]}")
            facts = [f for f in facts if not _has_pii(f)]

        if not facts:
            return memory_pb2.CaptureConversationResponse(
                extracted_facts=0,
                status="accepted",
            )

        # 3. Dedup
        to_insert, to_merge = deduplicate(user_id, facts)

        # 4. Embed + store
        total_facts = 0
        if to_insert:
            # Embed
            for fact in to_insert:
                try:
                    fact["fact_vector"] = embed(fact["content"])
                except Exception as e:
                    logger.error(f"Embedding failed for fact: {e}")
            to_insert = [f for f in to_insert if "fact_vector" in f]

            if to_insert:
                try:
                    insert_fact_vectors(to_insert)
                except Exception as e:
                    logger.error(f"Milvus insert failed: {e}")

                for fact in to_insert:
                    try:
                        insert_fact(fact)
                        total_facts += 1
                    except Exception as e:
                        logger.error(f"MySQL insert failed: {e}")

        # Merge facts
        for fact in to_merge:
            try:
                update_fact(fact["fact_id"], {
                    "content": fact["content"],
                    "importance": fact["importance"],
                    "session_id": fact.get("session_id", ""),
                })
                total_facts += 1
            except Exception as e:
                logger.error(f"Fact merge update failed: {e}")

        # Update user profile counts
        if total_facts > 0:
            try:
                update_profile_counts(
                    user_id, fact_delta=total_facts
                )
            except Exception:
                pass

        # Store conversation context in working memory
        if conversation.session_id:
            try:
                set_working_memory(
                    user_id=user_id,
                    session_id=conversation.session_id,
                    key=f"conversation_turn_{int(time.time())}",
                    value=json.dumps({
                        "session_id": conversation.session_id,
                        "message_count": len(conversation.messages),
                        "last_question": msgs[-1]["content"] if msgs else "",
                    }, ensure_ascii=False),
                )
            except Exception:
                pass

        logger.info(f"CaptureConversation done: extracted={total_facts}, status=merged")
        return memory_pb2.CaptureConversationResponse(
            extracted_facts=total_facts,
            status="merged" if to_merge else "distilled",
        )

    def IngestTaskResult(self, request, context):
        """Ingest results from async Kafka task processing."""
        task_id = request.task_id
        task_type = request.task_type
        status = request.status

        logger.info(f"IngestTaskResult: task={task_id}, type={task_type}, status={status}")

        if status == "failed":
            logger.error(f"Task {task_id} failed: {request.error_message}")
            return memory_pb2.CaptureConversationResponse(
                extracted_facts=0,
                status="failed",
            )

        # Process facts from async pipeline
        facts = []
        for proto_fact in request.facts:
            facts.append(_proto_fact_to_dict(proto_fact))

        if facts:
            for fact in facts:
                try:
                    insert_fact(fact)
                except Exception as e:
                    logger.error(f"Ingest fact insert failed: {e}")

        return memory_pb2.CaptureConversationResponse(
            extracted_facts=len(facts),
            status="completed",
        )


def _proto_fact_to_dict(pf) -> dict:
    return {
        "fact_id": pf.fact_id,
        "user_id": pf.user_id,
        "session_id": pf.session_id,
        "content": pf.content,
        "category": pf.category,
        "importance": pf.importance,
        "tags": list(pf.tags),
        "sources": [
            {"document_id": s.document_id, "document_name": s.document_name,
             "chunk_id": s.chunk_id, "excerpt": s.excerpt}
            for s in pf.sources
        ],
        "access_count": pf.access_count,
        "created_at_ms": pf.created_at_ms,
        "accessed_at_ms": pf.accessed_at_ms,
    }


def _has_pii(fact: dict) -> bool:
    import re
    patterns = [
        r"[1-9]\d{5}(?:19|20)\d{2}(?:0[1-9]|1[0-2])(?:0[1-9]|[12]\d|3[01])\d{3}[\dXx]",
        r"1[3-9]\d{9}",
        r"[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}",
    ]
    content = fact.get("content", "")
    for pat in patterns:
        if re.search(pat, content):
            return True
    return False
