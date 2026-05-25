"""MemoryAdminService — gRPC service for admin operations (stats, eviction, snapshots, health)."""
import asyncio
import time

from communication.grpc_server.generated import memory_pb2, memory_pb2_grpc
from infrastructure.milvus.client import count_vectors, ensure_collection
from infrastructure.mysql.client import (
    count_facts,
    upsert_user_profile,
)
from infrastructure.redis.client import evict_working_memory, get_working_memory
from infrastructure.llm.adapter import health_check as llm_health
from engine.snapshot_manager import create_snapshot
from loguru import logger


class MemoryAdminService(memory_pb2_grpc.MemoryAdminServiceServicer):

    def GetStats(self, request, context):
        """Get memory statistics for a user."""
        user_id = request.user_id
        logger.info(f"GetStats: user={user_id}")

        working_count = 0
        fact_count = 0
        vector_count = 0

        try:
            fact_count_async = count_facts(user_id)
            # Run async in sync context
            fact_count = int(fact_count_async) if fact_count_async else 0
        except Exception as e:
            logger.warning(f"Count facts failed: {e}")

        try:
            vector_count = count_vectors()
        except Exception:
            pass

        try:
            entries = get_working_memory(user_id, "*")
            working_count = len(entries) if entries else 0
        except Exception:
            pass

        stats = memory_pb2.MemoryStats(
            working_entry_count=working_count,
            atomic_fact_count=fact_count,
            episodic_summary_count=0,
            procedural_rule_count=0,
            total_vector_count=vector_count,
            avg_fact_importance=0.5,
        )

        return memory_pb2.GetMemoryStatsResponse(stats=stats)

    def EvictWorkingMemory(self, request, context):
        """Evict working memory for a user's session."""
        user_id = request.user_id
        session_id = request.session_id
        logger.info(f"EvictWorkingMemory: user={user_id}, session={session_id}")

        evicted = evict_working_memory(user_id, session_id)
        return memory_pb2.EvictWorkingMemoryResponse(evicted_count=evicted)

    def CreateSnapshot(self, request, context):
        """Create a full memory snapshot for a user."""
        user_id = request.user_id
        logger.info(f"CreateSnapshot: user={user_id}")

        try:
            # Ensure user profile exists
            upsert_user_profile(user_id)

            # Run async snapshot in sync context
            loop = asyncio.new_event_loop()
            try:
                snapshot = loop.run_until_complete(create_snapshot(user_id))
            finally:
                loop.close()

            return memory_pb2.CaptureConversationResponse(
                extracted_facts=snapshot.get("fact_count", 0),
                status="completed",
            )
        except Exception as e:
            logger.error(f"CreateSnapshot failed: {e}")
            return memory_pb2.CaptureConversationResponse(
                extracted_facts=0,
                status="failed",
            )

    def HealthCheck(self, request, context):
        """Health check for the memory service."""
        healthy = True
        mysql_ok = "up"
        milvus_ok = "up"
        redis_ok = "up"
        llm_ok = "up"

        # Check Milvus
        try:
            ensure_collection()
        except Exception as e:
            milvus_ok = f"down: {e}"
            healthy = False

        # Check LLM
        try:
            if not llm_health():
                llm_ok = "down"
                healthy = False
        except Exception as e:
            llm_ok = f"down: {e}"
            healthy = False

        # Check Redis
        try:
            from infrastructure.redis.client import get_client
            get_client().ping()
        except Exception as e:
            redis_ok = f"down: {e}"
            healthy = False

        # Check MySQL
        try:
            import asyncio
            from infrastructure.mysql.client import get_pool
            loop = asyncio.new_event_loop()
            try:
                pool = loop.run_until_complete(get_pool())
                if not pool:
                    mysql_ok = "down"
                    healthy = False
            finally:
                loop.close()
        except Exception as e:
            mysql_ok = f"down: {e}"
            healthy = False

        return memory_pb2.HealthCheckResponse(
            healthy=healthy,
            mysql_status=mysql_ok,
            milvus_status=milvus_ok,
            redis_status=redis_ok,
            llm_status=llm_ok,
        )
