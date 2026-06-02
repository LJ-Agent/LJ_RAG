package com.rag.communication.grpc.client;

import com.rag.common.constant.GrpcConstants;
import com.rag.common.exception.BusinessException;
import com.rag.common.result.ResultCodeEnum;
import com.rag.communication.grpc.proto.RetrievalRequest;
import com.rag.communication.grpc.proto.RetrievalResponse;
import com.rag.communication.grpc.proto.RetrievalServiceGrpc;
import io.grpc.ManagedChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class RetrievalServiceClient {

    private final RetrievalServiceGrpc.RetrievalServiceBlockingStub blockingStub;

    public RetrievalServiceClient(ManagedChannel retrievalChannel) {
        this.blockingStub = RetrievalServiceGrpc.newBlockingStub(retrievalChannel);
    }

    /**
     * 同步检索，从Python AI服务获取相关文档分块。
     */
    public RetrievalResponse retrieve(String query, List<Long> kbIds, int topK, float scoreThreshold) {
        try {
            RetrievalRequest request = RetrievalRequest.newBuilder()
                    .setQuery(query)
                    .addAllKbIds(kbIds)
                    .setTopK(topK)
                    .setScoreThreshold(scoreThreshold)
                    .build();

            log.info("gRPC检索请求: query={}, kbIds={}, topK={}", query, kbIds, topK);
            RetrievalResponse response = blockingStub
                    .withDeadlineAfter(GrpcConstants.GRPC_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .retrieve(request);

            log.info("gRPC检索完成: totalCount={}, latencyMs={}",
                    response.getTotalCount(), response.getLatencyMs());
            return response;
        } catch (Exception e) {
            log.error("gRPC检索失败: query={}", query, e);
            throw new BusinessException(ResultCodeEnum.QA_RETRIEVAL_ERROR);
        }
    }
}
