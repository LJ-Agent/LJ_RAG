"""RAG-MEMORY Service — main entry point.

Starts gRPC servers (MemoryWrite + MemorySearch + MemoryAdmin + MemoryCompliance)
and Kafka consumer for async task processing.
"""
import signal
import sys
import time

from common.config_loader import get_config
from common.logger import setup_logging, get_logger


def main():
    cfg = get_config()
    setup_logging(level=cfg["logging"]["level"], format=cfg["logging"]["format"])
    logger = get_logger()

    logger.info("=" * 60)
    logger.info("RAG Memory Management Service Starting...")
    logger.info("=" * 60)

    # TODO: Phase 4 — start gRPC servers and Kafka consumer
    logger.info("Service scaffold ready. gRPC servers and Kafka consumer pending Phase 4.")


if __name__ == "__main__":
    main()
