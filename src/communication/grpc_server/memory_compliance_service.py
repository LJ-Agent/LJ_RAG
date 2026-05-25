"""MemoryComplianceService — gRPC service for GDPR/privacy compliance operations."""
import asyncio

from communication.grpc_server.generated import memory_pb2, memory_pb2_grpc
from infrastructure.milvus.client import delete_facts_by_user
from infrastructure.mysql.client import (
    get_facts_by_user,
    get_episodes_by_user,
    get_rules_by_user,
    delete_facts_by_user as mysql_delete_facts,
    delete_episodes_by_user,
    delete_rules_by_user,
    insert_audit_log,
)
from infrastructure.redis.client import evict_working_memory
from loguru import logger


class MemoryComplianceService(memory_pb2_grpc.MemoryComplianceServiceServicer):

    def GetExportableData(self, request, context):
        """Export all user data for GDPR data portability."""
        user_id = request.user_id
        logger.info(f"GetExportableData: user={user_id}")

        loop = asyncio.new_event_loop()
        try:
            facts = loop.run_until_complete(get_facts_by_user(user_id, limit=100000))
            episodes = loop.run_until_complete(get_episodes_by_user(user_id))
            rules = loop.run_until_complete(get_rules_by_user(user_id))
        finally:
            loop.close()

        # Apply time range filter if specified
        start_ms = request.start_time_ms
        end_ms = request.end_time_ms
        if start_ms > 0 or end_ms > 0:
            facts = [
                f for f in facts
                if (start_ms == 0 or f.get("created_at_ms", 0) >= start_ms)
                and (end_ms == 0 or f.get("created_at_ms", 0) <= end_ms)
            ]

        audit_log = insert_audit_log(
            user_id=user_id,
            operation="EXPORT",
            target_type="USER",
            detail={"start_ms": start_ms, "end_ms": end_ms},
        )

        export = memory_pb2.ExportableData(
            facts=[_fact_to_proto(f) for f in facts],
            episodes=[_episode_to_proto(e) for e in episodes],
            rules=[_rule_to_proto(r) for r in rules],
        )

        logger.info(f"ExportableData: user={user_id}, facts={len(facts)}, "
                     f"episodes={len(episodes)}, rules={len(rules)}")

        return memory_pb2.GetExportableDataResponse(data=export)

    def ForgetUser(self, request, context):
        """Delete all memory data for a user (right to be forgotten)."""
        user_id = request.user_id
        logger.info(f"ForgetUser: user={user_id}")

        deleted_facts = 0
        deleted_episodes = 0
        deleted_rules = 0
        deleted_vectors = 0

        # Delete from MySQL
        loop = asyncio.new_event_loop()
        try:
            deleted_facts = loop.run_until_complete(mysql_delete_facts(user_id)) or 0
            deleted_episodes = loop.run_until_complete(delete_episodes_by_user(user_id)) or 0
            deleted_rules = loop.run_until_complete(delete_rules_by_user(user_id)) or 0
        finally:
            loop.close()

        # Delete from Milvus
        try:
            deleted_vectors = delete_facts_by_user(user_id)
        except Exception as e:
            logger.error(f"Milvus forget failed: {e}")

        # Evict all working memory
        try:
            evict_working_memory(user_id, "*")  # all sessions
        except Exception:
            pass

        # Audit
        audit_log = insert_audit_log(
            user_id=user_id,
            operation="FORGET",
            target_type="USER",
            detail={
                "deleted_facts": deleted_facts,
                "deleted_episodes": deleted_episodes,
                "deleted_rules": deleted_rules,
                "deleted_vectors": deleted_vectors,
            },
        )

        success = deleted_facts > 0 or deleted_vectors > 0
        logger.info(f"ForgetUser done: user={user_id}, success={success}, "
                     f"facts={deleted_facts}, episodes={deleted_episodes}, "
                     f"rules={deleted_rules}, vectors={deleted_vectors}")

        return memory_pb2.ForgetUserResponse(
            success=success,
            deleted_fact_count=deleted_facts,
            deleted_episode_count=deleted_episodes,
            deleted_rule_count=deleted_rules,
            deleted_vector_count=deleted_vectors,
        )


def _fact_to_proto(f: dict) -> memory_pb2.AtomicFact:
    return memory_pb2.AtomicFact(
        fact_id=f.get("fact_id", ""),
        user_id=f.get("user_id", 0),
        session_id=f.get("session_id", ""),
        content=f.get("content", ""),
        category=f.get("category", "factual"),
        importance=f.get("importance", 0.5),
        tags=f.get("tags", []) or [],
        access_count=f.get("access_count", 0),
        created_at_ms=f.get("created_at_ms", 0),
        accessed_at_ms=f.get("accessed_at_ms", 0),
    )


def _episode_to_proto(e: dict) -> memory_pb2.EpisodicSummary:
    return memory_pb2.EpisodicSummary(
        episode_id=e.get("episode_id", ""),
        user_id=e.get("user_id", 0),
        session_id=e.get("session_id", ""),
        summary=e.get("summary", ""),
        period=e.get("period", "session"),
        key_fact_ids=e.get("key_fact_ids", []) or [],
        start_time_ms=e.get("start_time_ms", 0),
        end_time_ms=e.get("end_time_ms", 0),
        created_at_ms=e.get("created_at_ms", 0),
    )


def _rule_to_proto(r: dict) -> memory_pb2.ProceduralRule:
    return memory_pb2.ProceduralRule(
        rule_id=r.get("rule_id", ""),
        user_id=r.get("user_id", 0),
        rule_content=r.get("rule_content", ""),
        category=r.get("category", "preference"),
        supporting_fact_ids=r.get("supporting_fact_ids", []) or [],
        confidence=r.get("confidence", 0.5),
        activation_count=r.get("activation_count", 0),
        created_at_ms=r.get("created_at_ms", 0),
        last_activated_ms=r.get("last_activated_ms", 0),
    )
