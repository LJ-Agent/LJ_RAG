"""MemorySearchService — gRPC service for searching and retrieving user memories."""
import time

from communication.grpc_server.generated import memory_pb2, memory_pb2_grpc
from engine.retriever import retrieve
from engine.fusion_ranker import top_k
from infrastructure.redis.client import get_working_memory
from loguru import logger


class MemorySearchService(memory_pb2_grpc.MemorySearchServiceServicer):

    def Search(self, request, context):
        """Full hybrid memory search across all layers."""
        user_id = request.user_id
        query = request.query
        categories = list(request.categories) if request.categories else None

        logger.info(f"MemorySearch: user={user_id}, query='{query[:100]}', "
                     f"top_k={request.top_k}, categories={categories}")

        result = retrieve(
            user_id=user_id,
            query=query,
            top_k=request.top_k if request.top_k > 0 else 20,
            score_threshold=request.score_threshold if request.score_threshold > 0 else 0.0,
            categories=categories,
            time_range_start_ms=request.time_range_start_ms if request.time_range_start_ms > 0 else None,
            time_range_end_ms=request.time_range_end_ms if request.time_range_end_ms > 0 else None,
        )

        # Rank with fusion scorer
        if result.get("facts"):
            result["facts"] = top_k(result["facts"], request.top_k if request.top_k > 0 else 10)

        search_result = memory_pb2.MemorySearchResult(
            facts=[_dict_to_proto_fact(f) for f in result.get("facts", [])],
            episodes=[_dict_to_proto_episode(e) for e in result.get("episodes", [])],
            rules=[_dict_to_proto_rule(r) for r in result.get("rules", [])],
            working_entries=[_dict_to_proto_working(w) for w in result.get("working", [])],
            total_latency_ms=result.get("latency_ms", 0),
        )

        return memory_pb2.MemorySearchResponse(result=search_result)

    def GetSessionContext(self, request, context):
        """Retrieve full context for a specific session (facts + working memory)."""
        user_id = request.user_id
        logger.info(f"GetSessionContext: user={user_id}")

        result = retrieve(
            user_id=user_id,
            query=request.query or "",
            top_k=request.top_k if request.top_k > 0 else 50,
            session_id=None,
        )

        search_result = memory_pb2.MemorySearchResult(
            facts=[_dict_to_proto_fact(f) for f in result.get("facts", [])],
            episodes=[_dict_to_proto_episode(e) for e in result.get("episodes", [])],
            rules=[_dict_to_proto_rule(r) for r in result.get("rules", [])],
            total_latency_ms=result.get("latency_ms", 0),
        )

        return memory_pb2.MemorySearchResponse(result=search_result)

    def GetWorkingMemory(self, request, context):
        """Get current working memory entries for a user."""
        user_id = request.user_id
        logger.info(f"GetWorkingMemory: user={user_id}")

        # Get all active sessions' working memory
        # This retrieves from all session keys in Redis
        entries: list = []
        try:
            entries = get_working_memory(user_id, "*")  # wildcard session
        except Exception:
            # Fallback: return empty
            pass

        search_result = memory_pb2.MemorySearchResult(
            working_entries=[_dict_to_proto_working(w) for w in entries],
            total_latency_ms=0,
        )

        return memory_pb2.MemorySearchResponse(result=search_result)


def _dict_to_proto_fact(f: dict) -> memory_pb2.AtomicFact:
    return memory_pb2.AtomicFact(
        fact_id=f.get("fact_id", ""),
        user_id=f.get("user_id", 0),
        session_id=f.get("session_id", ""),
        content=f.get("content", ""),
        category=f.get("category", "factual"),
        importance=f.get("importance", 0.5),
        tags=f.get("tags", []),
        access_count=f.get("access_count", 0),
        created_at_ms=f.get("created_at_ms", 0),
        accessed_at_ms=f.get("accessed_at_ms", 0),
    )


def _dict_to_proto_episode(e: dict) -> memory_pb2.EpisodicSummary:
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


def _dict_to_proto_rule(r: dict) -> memory_pb2.ProceduralRule:
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


def _dict_to_proto_working(w: dict) -> memory_pb2.WorkingMemoryEntry:
    return memory_pb2.WorkingMemoryEntry(
        entry_id=w.get("entry_id", ""),
        user_id=w.get("user_id", 0),
        session_id=w.get("session_id", ""),
        key=w.get("key", ""),
        value=w.get("value", ""),
        ttl_seconds=w.get("ttl_seconds", 3600),
        created_at_ms=w.get("created_at_ms", 0),
    )
