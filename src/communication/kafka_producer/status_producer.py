"""Kafka producer for reporting task processing status back to Java backend."""
import json
import time
from typing import Any

from kafka import KafkaProducer
from loguru import logger

from common.config_loader import get_config

_producer: KafkaProducer | None = None


def get_producer() -> KafkaProducer:
    global _producer
    if _producer is not None:
        return _producer
    cfg = get_config()["kafka"]
    _producer = KafkaProducer(
        bootstrap_servers=cfg["bootstrap_servers"],
        value_serializer=lambda v: json.dumps(v, ensure_ascii=False).encode("utf-8"),
        retries=3,
        acks="all",
    )
    logger.info(f"Status producer connected: {cfg['bootstrap_servers']}")
    return _producer


def close_producer() -> None:
    global _producer
    if _producer:
        _producer.flush()
        _producer.close()
        _producer = None


def report_complete(task_id: int, task_type: str, facts: list[dict[str, Any]] | None = None) -> None:
    """Report task completion to Kafka."""
    cfg = get_config()["kafka"]
    topic = cfg["topics"]["status"]
    producer = get_producer()

    message = {
        "task_id": task_id,
        "task_type": task_type,
        "status": "completed",
        "timestamp_ms": int(time.time() * 1000),
        "facts": facts or [],
        "error_message": "",
    }
    try:
        future = producer.send(topic, message)
        future.get(timeout=10)
        logger.info(f"Reported complete: task={task_id}, type={task_type}, facts={len(facts or [])}")
    except Exception as e:
        logger.error(f"Failed to report complete: {e}")


def report_failed(task_id: int, task_type: str, error: str) -> None:
    """Report task failure to Kafka."""
    cfg = get_config()["kafka"]
    topic = cfg["topics"]["status"]
    producer = get_producer()

    message = {
        "task_id": task_id,
        "task_type": task_type,
        "status": "failed",
        "timestamp_ms": int(time.time() * 1000),
        "error_message": error,
        "facts": [],
    }
    try:
        future = producer.send(topic, message)
        future.get(timeout=10)
        logger.info(f"Reported failed: task={task_id}, type={task_type}")
    except Exception as e:
        logger.error(f"Failed to report failure: {e}")
