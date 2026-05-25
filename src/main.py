"""RAG-MEMORY Service — main entry point.

Starts gRPC servers (MemoryWrite + MemorySearch + MemoryAdmin + MemoryCompliance)
and Kafka consumer for async task processing.
"""
import signal
import sys
import time
from concurrent import futures

import grpc

from common.config_loader import get_config
from common.logger import setup_logging, get_logger
from communication.grpc_server.generated import memory_pb2_grpc
from communication.grpc_server.memory_write_service import MemoryWriteService
from communication.grpc_server.memory_search_service import MemorySearchService
from communication.grpc_server.memory_admin_service import MemoryAdminService
from communication.grpc_server.memory_compliance_service import MemoryComplianceService
from communication.kafka_consumer.memory_consumer import (
    MemoryTaskConsumer,
    create_distill_handler,
    create_forget_handler,
    create_archive_handler,
)
from communication.kafka_producer.status_producer import close_producer
from infrastructure.milvus.client import connect_milvus, disconnect_milvus, ensure_collection
from infrastructure.redis.client import get_client as get_redis_client, close as close_redis


def main():
    cfg = get_config()
    setup_logging(level=cfg["logging"]["level"], format=cfg["logging"]["format"])
    logger = get_logger()

    logger.info("=" * 60)
    logger.info("RAG Memory Management Service Starting...")
    logger.info("=" * 60)

    # Initialize infrastructure
    logger.info("Connecting to infrastructure...")
    connect_milvus()
    ensure_collection()
    get_redis_client()
    logger.info("Infrastructure connections established")

    # Create gRPC servers
    write_port = str(cfg["grpc"]["write_port"])
    search_port = str(cfg["grpc"]["search_port"])
    max_workers = int(cfg["grpc"].get("max_workers", 10))

    write_server = grpc.server(
        futures.ThreadPoolExecutor(max_workers=max_workers),
        options=[("grpc.keepalive_time_ms", 30000)],
    )
    search_server = grpc.server(
        futures.ThreadPoolExecutor(max_workers=max_workers),
        options=[("grpc.keepalive_time_ms", 30000)],
    )

    # Register services
    # Write server hosts: WriteService + AdminService + ComplianceService
    memory_pb2_grpc.add_MemoryWriteServiceServicer_to_server(MemoryWriteService(), write_server)
    memory_pb2_grpc.add_MemoryAdminServiceServicer_to_server(MemoryAdminService(), write_server)
    memory_pb2_grpc.add_MemoryComplianceServiceServicer_to_server(MemoryComplianceService(), write_server)

    # Search server hosts: SearchService only (higher throughput)
    memory_pb2_grpc.add_MemorySearchServiceServicer_to_server(MemorySearchService(), search_server)

    # Start gRPC servers
    write_server.add_insecure_port(f"[::]:{write_port}")
    search_server.add_insecure_port(f"[::]:{search_port}")
    write_server.start()
    search_server.start()
    logger.info(f"gRPC servers started — Write:0.0.0.0:{write_port}, Search:0.0.0.0:{search_port}")

    # Start Kafka consumer
    kafka_cfg = cfg["kafka"]
    consumer = MemoryTaskConsumer()

    # Register handlers
    consumer.register_handler(kafka_cfg["topics"]["distill"], create_distill_handler())
    consumer.register_handler(kafka_cfg["topics"]["forget"], create_forget_handler())
    consumer.register_handler(kafka_cfg["topics"]["archive"], create_archive_handler())

    consumer.start()
    logger.info(f"Kafka consumer started: topics={[kafka_cfg['topics']['distill'], kafka_cfg['topics']['forget'], kafka_cfg['topics']['archive']]}")

    # Graceful shutdown
    def _shutdown(sig, frame):
        logger.info(f"Received signal {sig}, shutting down...")
        consumer.stop()
        write_server.stop(grace=10)
        search_server.stop(grace=10)
        disconnect_milvus()
        close_redis()
        close_producer()
        logger.info("Shutdown complete")
        sys.exit(0)

    signal.signal(signal.SIGINT, _shutdown)
    signal.signal(signal.SIGTERM, _shutdown)

    logger.info("RAG Memory Management Service is ready")

    # Keep main thread alive
    try:
        while True:
            time.sleep(30)
    except KeyboardInterrupt:
        _shutdown(signal.SIGINT, None)


if __name__ == "__main__":
    main()
