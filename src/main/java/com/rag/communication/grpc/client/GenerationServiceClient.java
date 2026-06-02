package com.rag.communication.grpc.client;

import com.rag.common.constant.GrpcConstants;
import com.rag.common.exception.BusinessException;
import com.rag.common.result.ResultCodeEnum;
import com.rag.communication.grpc.proto.GenerationRequest;
import com.rag.communication.grpc.proto.GenerationResponse;
import com.rag.communication.grpc.proto.GenerationServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Slf4j
@Component
public class GenerationServiceClient {

    private final GenerationServiceGrpc.GenerationServiceBlockingStub blockingStub;
    private final GenerationServiceGrpc.GenerationServiceStub asyncStub;

    public GenerationServiceClient(ManagedChannel generationChannel) {
        this.blockingStub = GenerationServiceGrpc.newBlockingStub(generationChannel);
        this.asyncStub = GenerationServiceGrpc.newStub(generationChannel);
    }

    /**
     * 非流式生成，一次性返回完整答案。
     */
    public GenerationResponse generate(String query, List<String> contexts) {
        try {
            GenerationRequest request = GenerationRequest.newBuilder()
                    .setQuery(query)
                    .addAllContexts(contexts)
                    .build();

            log.info("gRPC生成请求: query={}, contextCount={}", query, contexts.size());
            GenerationResponse response = blockingStub
                    .withDeadlineAfter(GrpcConstants.GRPC_TIMEOUT_SECONDS * 3, TimeUnit.SECONDS)
                    .generate(request);

            log.info("gRPC生成完成: tokenCount={}", response.getTokenCount());
            return response;
        } catch (Exception e) {
            log.error("gRPC生成失败: query={}", query, e);
            throw new BusinessException(ResultCodeEnum.QA_GENERATION_ERROR);
        }
    }

    /**
     * 流式生成，通过回调逐个处理token。
     */
    public void generateStream(String query, List<String> contexts,
                                Consumer<GenerationResponse> onToken,
                                Runnable onComplete,
                                Consumer<Throwable> onError) {
        GenerationRequest request = GenerationRequest.newBuilder()
                .setQuery(query)
                .addAllContexts(contexts)
                .build();

        asyncStub.withDeadlineAfter(GrpcConstants.GRPC_TIMEOUT_SECONDS * 5, TimeUnit.SECONDS)
                .generateStream(request, new StreamObserver<>() {
                    @Override
                    public void onNext(GenerationResponse response) {
                        onToken.accept(response);
                    }

                    @Override
                    public void onError(Throwable t) {
                        log.error("gRPC流式生成失败: query={}", query, t);
                        onError.accept(t);
                    }

                    @Override
                    public void onCompleted() {
                        log.info("gRPC流式生成完成: query={}", query);
                        onComplete.run();
                    }
                });
    }

    /**
     * 返回流式迭代器，由调用方自己控制迭代。
     */
    public Iterator<GenerationResponse> generateStreamIterator(String query, List<String> contexts) {
        try {
            GenerationRequest request = GenerationRequest.newBuilder()
                    .setQuery(query)
                    .addAllContexts(contexts)
                    .build();

            return blockingStub
                    .withDeadlineAfter(GrpcConstants.GRPC_TIMEOUT_SECONDS * 5, TimeUnit.SECONDS)
                    .generateStream(request);
        } catch (Exception e) {
            log.error("gRPC流式生成失败: query={}", query, e);
            throw new BusinessException(ResultCodeEnum.QA_GENERATION_ERROR);
        }
    }
}
