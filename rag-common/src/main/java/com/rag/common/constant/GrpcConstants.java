package com.rag.common.constant;

public interface GrpcConstants {

    /** gRPC调用超时（秒） */
    int GRPC_TIMEOUT_SECONDS = 10;

    /** gRPC最大重试次数 */
    int GRPC_MAX_RETRIES = 3;

    /** gRPC重试间隔（毫秒） */
    long GRPC_RETRY_DELAY_MS = 1000L;

    /** QUE Engine — max sub-queries per request */
    public static final int QUE_MAX_SUB_QUERIES = 8;
    /** QUE Engine — default timeout in seconds */
    public static final int QUE_TIMEOUT_SECONDS = 60;
}