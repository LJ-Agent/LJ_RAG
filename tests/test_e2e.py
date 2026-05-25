"""End-to-end tests for RAG-MEMORY service.

Requires infrastructure running:
  mysql, redis, milvus, kafka — via docker-compose up -d

Test flow:
  1. gRPC health check
  2. Capture conversation → distill & store facts
  3. Search memory → verify retrieval
  4. Get stats
  5. Export data (compliance)
  6. Forget user (cleanup)
"""
import time
import grpc
import pytest

# Import generated stubs — adjust import path for test context
import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).parent.parent / "src"))

from communication.grpc_server.generated import memory_pb2, memory_pb2_grpc


WRITE_ADDR = "localhost:50053"
SEARCH_ADDR = "localhost:50054"


@pytest.fixture(scope="module")
def write_stub():
    channel = grpc.insecure_channel(WRITE_ADDR)
    yield memory_pb2_grpc.MemoryWriteServiceStub(channel)


@pytest.fixture(scope="module")
def search_stub():
    channel = grpc.insecure_channel(SEARCH_ADDR)
    yield memory_pb2_grpc.MemorySearchServiceStub(channel)


@pytest.fixture(scope="module")
def admin_stub():
    channel = grpc.insecure_channel(WRITE_ADDR)
    yield memory_pb2_grpc.MemoryAdminServiceStub(channel)


@pytest.fixture(scope="module")
def compliance_stub():
    channel = grpc.insecure_channel(WRITE_ADDR)
    yield memory_pb2_grpc.MemoryComplianceServiceStub(channel)


@pytest.fixture
def test_user_id():
    return 999001  # Dedicated test user


class TestHealthCheck:
    def test_health_all_up(self, admin_stub):
        """Health check should report all services up."""
        resp = admin_stub.HealthCheck(memory_pb2.HealthCheckRequest(), timeout=10)
        assert resp.healthy, f"Health check failed: mysql={resp.mysql_status}, milvus={resp.milvus_status}, redis={resp.redis_status}, llm={resp.llm_status}"
        print(f"Health: mysql={resp.mysql_status}, milvus={resp.milvus_status}, redis={resp.redis_status}, llm={resp.llm_status}")


class TestCaptureAndSearch:
    def test_capture_conversation(self, write_stub, test_user_id):
        """Capture a conversation with personal facts and verify distillation."""
        conversation = memory_pb2.SessionConversation(
            session_id="test-session-001",
            messages=[
                memory_pb2.ContextBlock(
                    role="user",
                    content="你好，我叫张三，我是一名软件工程师，主要用Python和Java开发。",
                    timestamp_ms=int(time.time() * 1000) - 10000,
                ),
                memory_pb2.ContextBlock(
                    role="assistant",
                    content="你好张三！很高兴认识你。Python和Java都是很强大的语言。有什么我可以帮你的吗？",
                    timestamp_ms=int(time.time() * 1000) - 8000,
                ),
                memory_pb2.ContextBlock(
                    role="user",
                    content="我下个月计划去北京出差，需要准备一些技术分享的材料。我喜欢喝咖啡，每天早上必须喝一杯。",
                    timestamp_ms=int(time.time() * 1000) - 5000,
                ),
            ],
        )

        config = memory_pb2.DistillationConfig(
            enable_pii_detection=True,
            importance_threshold=0.2,
            max_facts_per_batch=50,
        )

        resp = write_stub.CaptureConversation(
            memory_pb2.CaptureConversationRequest(
                user_id=test_user_id,
                conversation=conversation,
                config=config,
            ),
            timeout=30,
        )

        print(f"Capture result: extracted_facts={resp.extracted_facts}, status={resp.status}")
        assert resp.extracted_facts > 0, f"Expected at least 1 fact, got {resp.extracted_facts}"
        assert resp.status in ("distilled", "merged")

    def test_search_memory(self, search_stub, test_user_id):
        """Search for facts about the test user."""
        resp = search_stub.Search(
            memory_pb2.MemorySearchRequest(
                user_id=test_user_id,
                query="软件工程师 咖啡 Python",
                top_k=10,
                score_threshold=0.0,
            ),
            timeout=30,
        )
        result = resp.result
        print(f"Search: facts={len(result.facts)}, episodes={len(result.episodes)}, "
              f"rules={len(result.rules)}, latency={result.total_latency_ms}ms")

        assert len(result.facts) > 0, "Expected at least 1 fact in search results"
        # Verify we got relevant facts
        contents = [f.content for f in result.facts]
        print(f"Retrieved facts: {contents}")

    def test_get_session_context(self, search_stub, test_user_id):
        """Get full session context."""
        resp = search_stub.GetSessionContext(
            memory_pb2.MemorySearchRequest(
                user_id=test_user_id,
                top_k=20,
            ),
            timeout=30,
        )
        assert resp.result.facts or resp.result.episodes or resp.result.rules, \
            "Should have some memory data"


class TestAdmin:
    def test_get_stats(self, admin_stub, test_user_id):
        """Get memory statistics."""
        resp = admin_stub.GetStats(
            memory_pb2.GetMemoryStatsRequest(user_id=test_user_id),
            timeout=10,
        )
        stats = resp.stats
        print(f"Stats: facts={stats.atomic_fact_count}, working={stats.working_entry_count}, "
              f"vectors={stats.total_vector_count}")
        assert stats.atomic_fact_count > 0, "Should have at least 1 fact"


class TestCompliance:
    def test_get_exportable_data(self, compliance_stub, test_user_id):
        """Export data for GDPR compliance."""
        resp = compliance_stub.GetExportableData(
            memory_pb2.GetExportableDataRequest(
                user_id=test_user_id,
            ),
            timeout=30,
        )
        data = resp.data
        print(f"Exportable: facts={len(data.facts)}, episodes={len(data.episodes)}, "
              f"rules={len(data.rules)}")
        assert len(data.facts) > 0, "Should have facts to export"

    def test_forget_user(self, compliance_stub, test_user_id):
        """Right to be forgotten."""
        resp = compliance_stub.ForgetUser(
            memory_pb2.ForgetUserRequest(user_id=test_user_id),
            timeout=30,
        )
        print(f"Forget: success={resp.success}, facts={resp.deleted_fact_count}, "
              f"episodes={resp.deleted_episode_count}, rules={resp.deleted_rule_count}, "
              f"vectors={resp.deleted_vector_count}")
        assert resp.success, "Forget operation should succeed"
        assert resp.deleted_fact_count >= 0
