package com.rag.communication.grpc.client;

import com.rag.common.constant.GrpcConstants;
import com.rag.common.exception.BusinessException;
import com.rag.common.result.ResultCodeEnum;
import com.rag.communication.grpc.proto.que.v2.QueEngineServiceGrpc;
import com.rag.communication.grpc.proto.que.v2.QueRequest;
import com.rag.communication.grpc.proto.que.v2.QueResponse;
import com.rag.communication.grpc.proto.que.v2.SearchResult;
import io.grpc.ManagedChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * gRPC client for QUE Engine (Query Understanding & Execution).
 *
 * QUE Engine replaces direct RetrievalService calls. It:
 * 1. Analyzes intent
 * 2. Rewrites and expands the query
 * 3. Plans a DAG of sub-queries
 * 4. Routes each to the appropriate backend (RAG retrieval, memory, LLM)
 * 5. Executes in parallel waves
 * 6. Synthesizes results into LLM-ready context
 */
@Slf4j
@Component
public class QueServiceClient {

    private final QueEngineServiceGrpc.QueEngineServiceBlockingStub blockingStub;

    public QueServiceClient(ManagedChannel queChannel) {
        this.blockingStub = QueEngineServiceGrpc.newBlockingStub(queChannel);
    }

    /**
     * Execute the full QUE pipeline.
     *
     * @param query   user's raw question
     * @param context context map (kb_ids, user_id, session_id, etc.)
     * @param timeoutMs total timeout in milliseconds
     * @return QUE response with synthesized context and execution trace
     */
    public QueResponse execute(String query, Map<String, String> context, int timeoutMs) {
        try {
            QueRequest request = QueRequest.newBuilder()
                    .setQuery(query)
                    .putAllContext(context)
                    .setMaxSubQueries(GrpcConstants.QUE_MAX_SUB_QUERIES)
                    .setTimeoutMs(timeoutMs)
                    .setEnableHyde(true)
                    .setEnableMultiQuery(true)
                    .build();

            log.info("QUE Execute: query='{}', contextKeys={}", query, context.keySet());
            QueResponse response = blockingStub
                    .withDeadlineAfter(timeoutMs, TimeUnit.MILLISECONDS)
                    .execute(request);

            log.info("QUE completed: intent={}, subQueries={}, latency={}ms, context={}chars",
                    response.getPlan().getPrimaryIntent(),
                    response.getPlan().getTotalQueries(),
                    response.getTotalLatencyMs(),
                    response.getSynthesizedContext().length());

            return response;
        } catch (Exception e) {
            log.error("QUE execution failed: query={}", query, e);
            throw new BusinessException(ResultCodeEnum.QA_RETRIEVAL_ERROR);
        }
    }

    /**
     * Extract chunk contents from QUE response as a list of plain strings.
     * Maintains backward compatibility with existing context-building code.
     */
    public static List<String> extractContexts(QueResponse response) {
        return response.getSubResultsList().stream()
                .filter(r -> r.getSuccess())
                .flatMap(r -> r.getResultsList().stream())
                .map(SearchResult::getContent)
                .collect(Collectors.toList());
    }

    /**
     * Convert QUE SearchResult to a map format for source doc display.
     */
    public static List<Map<String, Object>> extractSourceDocs(QueResponse response) {
        return response.getSubResultsList().stream()
                .filter(r -> r.getSuccess())
                .flatMap(r -> r.getResultsList().stream())
                .map(sr -> {
                    Map<String, Object> doc = new HashMap<>();
                    doc.put("chunkId", sr.getId());
                    doc.put("content", sr.getContent());
                    doc.put("score", sr.getScore());
                    doc.put("sourceBackend", sr.getSourceBackend());
                    // Extract metadata
                    Map<String, String> meta = sr.getMetadataMap();
                    doc.put("documentId", Long.parseLong(meta.getOrDefault("document_id", "0")));
                    doc.put("documentName", meta.getOrDefault("document_name", ""));
                    doc.put("chunkIndex", Integer.parseInt(meta.getOrDefault("chunk_index", "0")));
                    return doc;
                })
                .collect(Collectors.toList());
    }

    /**
     * Build context map from user ID, session ID, and KB IDs.
     */
    public static Map<String, String> buildContext(Long userId, Long sessionId, List<Long> kbIds) {
        Map<String, String> context = new HashMap<>();
        if (userId != null) {
            context.put("user_id", String.valueOf(userId));
        }
        if (sessionId != null) {
            context.put("session_id", String.valueOf(sessionId));
        }
        if (kbIds != null && !kbIds.isEmpty()) {
            context.put("kb_ids", kbIds.stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(",")));
        }
        return context;
    }
}
