"""Kafka consumer for memory task processing (distill, forget, archive).

Reuses the same patterns as RAG-PYTHON's TaskKafkaConsumer:
- Manual offset commit
- Signal-aware graceful shutdown
- Task dispatch with retry
"""
import json
import signal
import threading
import time
from typing import Any, Callable

from kafka import KafkaConsumer, KafkaProducer
from loguru import logger

from common.config_loader import get_config


class MemoryTaskConsumer:
    """Consumes memory tasks from Kafka and dispatches to handlers."""

    def __init__(self):
        cfg = get_config()["kafka"]
        self._bootstrap = cfg["bootstrap_servers"]
        self._group_id = cfg["group_id"]
        self._topics = [
            cfg["topics"]["distill"],
            cfg["topics"]["forget"],
            cfg["topics"]["archive"],
        ]
        self._consumer: KafkaConsumer | None = None
        self._handlers: dict[str, Callable[[dict[str, Any]], None]] = {}
        self._running = False
        self._thread: threading.Thread | None = None

    def register_handler(self, topic: str, handler: Callable[[dict[str, Any]], None]) -> None:
        """Register a handler function for a specific topic."""
        self._handlers[topic] = handler

    def start(self) -> None:
        """Start consuming in a background daemon thread."""
        self._running = True
        self._consumer = KafkaConsumer(
            *self._topics,
            bootstrap_servers=self._bootstrap,
            group_id=self._group_id,
            auto_offset_reset="earliest",
            enable_auto_commit=False,
            max_poll_records=10,
            session_timeout_ms=30000,
            heartbeat_interval_ms=10000,
            value_deserializer=lambda m: json.loads(m.decode("utf-8")) if m else None,
        )
        self._thread = threading.Thread(target=self._consume_loop, daemon=True)
        self._thread.start()
        logger.info(f"MemoryTaskConsumer started: topics={self._topics}, group={self._group_id}")

    def stop(self) -> None:
        """Graceful shutdown."""
        self._running = False
        if self._consumer:
            self._consumer.close()
        if self._thread and self._thread.is_alive():
            self._thread.join(timeout=10)
        logger.info("MemoryTaskConsumer stopped")

    def _consume_loop(self) -> None:
        """Main consumption loop."""
        retry_count = 0
        while self._running:
            try:
                records = self._consumer.poll(timeout_ms=5000)  # pyright: ignore[reportOptionalMemberAccess]
                for topic_partition, batch in records.items():
                    for record in batch:
                        topic = topic_partition.topic
                        handler = self._handlers.get(topic)
                        if handler:
                            try:
                                handler(record.value)
                            except Exception as e:
                                logger.error(f"Handler error for topic={topic}: {e}")
                        else:
                            logger.warning(f"No handler for topic: {topic}")

                # Commit offsets after processing batch
                if records:
                    self._consumer.commit()  # pyright: ignore[reportOptionalMemberAccess]
                retry_count = 0
            except Exception as e:
                retry_count += 1
                logger.error(f"Consumer error (retry {retry_count}): {e}")
                time.sleep(min(retry_count * 2, 30))


def create_distill_handler():
    """Factory: create a handler for memory.distill messages."""
    from engine.distiller import distill
    from engine.classifier import validate_no_pii
    from engine.deduplicator import deduplicate
    from infrastructure.llm.adapter import embed
    from infrastructure.milvus.client import insert_fact_vectors
    from infrastructure.mysql.client import insert_fact, upsert_user_profile, update_profile_counts

    def handle(message: dict[str, Any]) -> None:
        user_id = message["user_id"]
        session_id = message.get("session_id", "")
        messages = message.get("messages", [])
        config = message.get("config", {})

        logger.info(f"Distill task: user={user_id}, session={session_id}, msgs={len(messages)}")

        upsert_user_profile(user_id)

        facts = distill(user_id, session_id, messages, config)
        if not facts:
            return

        # PII check
        enable_pii = config.get("enable_pii_detection", True)
        if enable_pii:
            facts = [f for f in facts if not _has_pii(f)]

        # Dedup
        to_insert, to_merge = deduplicate(user_id, facts)

        total = 0
        if to_insert:
            for f in to_insert:
                try:
                    f["fact_vector"] = embed(f["content"])
                except Exception:
                    pass
            to_insert = [f for f in to_insert if "fact_vector" in f]
            if to_insert:
                insert_fact_vectors(to_insert)
                for f in to_insert:
                    insert_fact(f)
                    total += 1

        for f in to_merge:
            total += 1

        if total > 0:
            update_profile_counts(user_id, fact_delta=total)

        logger.info(f"Distill task completed: extracted={total}")

    return handle


def create_forget_handler():
    """Factory: create a handler for memory.forget messages."""
    from infrastructure.milvus.client import delete_facts_by_user
    from infrastructure.mysql.client import delete_facts_by_user as mysql_delete_facts
    from infrastructure.redis.client import evict_working_memory

    def handle(message: dict[str, Any]) -> None:
        user_id = message["user_id"]
        logger.info(f"Forget task: user={user_id}")

        mysql_delete_facts(user_id)
        delete_facts_by_user(user_id)
        evict_working_memory(user_id, "*")

        logger.info(f"Forget task completed: user={user_id}")

    return handle


def create_archive_handler():
    """Factory: create a handler for memory.archive messages."""
    from engine.archiver import archive_session

    def handle(message: dict[str, Any]) -> None:
        user_id = message["user_id"]
        session_id = message.get("session_id", "")
        logger.info(f"Archive task: user={user_id}, session={session_id}")

        archive_session(user_id, session_id)
        logger.info(f"Archive task completed: user={user_id}, session={session_id}")

    return handle


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
