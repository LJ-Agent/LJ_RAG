package com.rag.common.constant;

public interface GrpcConstants {

    /** gRPC调用超时（秒） */
    int GRPC_TIMEOUT_SECONDS = 10;

    /** gRPC最大重试次数 */
    int GRPC_MAX_RETRIES = 3;

    /** gRPC重试间隔（毫秒） */
    long GRPC_RETRY_DELAY_MS = 1000L;
}
