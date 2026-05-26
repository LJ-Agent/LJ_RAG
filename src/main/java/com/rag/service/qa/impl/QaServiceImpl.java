package com.rag.service.qa.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rag.common.constant.CacheConstants;
import com.rag.common.enums.DocumentStatus;
import com.rag.common.exception.BusinessException;
import com.rag.common.result.Result;
import com.rag.common.result.ResultCodeEnum;
import com.rag.communication.grpc.client.GenerationServiceClient;
import com.rag.communication.grpc.client.RetrievalServiceClient;
import com.rag.communication.grpc.proto.DocumentChunk;
import com.rag.communication.grpc.proto.GenerationResponse;
import com.rag.communication.grpc.proto.RetrievalResponse;
import com.rag.domain.entity.ChatRecord;
import com.rag.domain.entity.ChatSession;
import com.rag.domain.entity.Document;
import com.rag.domain.mapper.ChatRecordMapper;
import com.rag.domain.mapper.ChatSessionMapper;
import com.rag.domain.mapper.DocumentMapper;
import com.rag.service.qa.QaService;
import com.rag.service.qa.dto.AnswerVO;
import com.rag.service.qa.dto.ChatHistoryVO;
import com.rag.service.qa.dto.QuestionDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class QaServiceImpl implements QaService {

    private final RetrievalServiceClient retrievalClient;
    private final GenerationServiceClient generationClient;
    private final ChatRecordMapper chatRecordMapper;
    private final ChatSessionMapper sessionMapper;
    private final DocumentMapper documentMapper;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public Result<AnswerVO> chat(QuestionDTO dto, Long userId) {
        // 1. 检查缓存（含 kbIds 避免不同知识库污染）
        String kbIdsKey = dto.getKbIds().stream().sorted().map(String::valueOf)
                .collect(java.util.stream.Collectors.joining(","));
        String cacheKey = CacheConstants.QA_CACHE_PREFIX + kbIdsKey + ":"
                + (dto.getQuestion().hashCode() & 0x7fffffff);
        String cached = stringRedisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            log.info("命中问答缓存: key={}", cacheKey);
            return Result.success(JSONUtil.toBean(cached, AnswerVO.class));
        }

        // 2. gRPC检索
        long startTime = System.currentTimeMillis();
        RetrievalResponse retrievalResponse = retrievalClient.retrieve(
                dto.getQuestion(), dto.getKbIds(), dto.getTopK(), dto.getScoreThreshold());

        // 3. 构建上下文
        List<String> contexts = new ArrayList<>();
        List<AnswerVO.SourceDoc> sourceDocs = new ArrayList<>();
        for (DocumentChunk chunk : retrievalResponse.getChunksList()) {
            contexts.add(chunk.getContent());

            AnswerVO.SourceDoc src = new AnswerVO.SourceDoc();
            src.setDocumentId(chunk.getDocumentId());
            src.setDocumentName(chunk.getDocumentName());
            src.setChunkId(chunk.getChunkId());
            src.setChunkIndex(chunk.getChunkIndex());
            src.setContent(chunk.getContent());
            src.setScore(chunk.getScore());
            sourceDocs.add(src);
        }

        // 4. gRPC生成
        GenerationResponse generationResponse = generationClient.generate(dto.getQuestion(), contexts);
        int latency = (int) (System.currentTimeMillis() - startTime);

        // 5. 构建结果
        AnswerVO vo = new AnswerVO();
        vo.setAnswer(generationResponse.getContent());
        vo.setSourceDocs(sourceDocs);
        vo.setTokenCount(generationResponse.getTokenCount());
        vo.setLatencyMs(latency);

        // 6. 保存问答记录
        ChatRecord record = new ChatRecord();
        record.setUserId(userId);
        record.setSessionId(dto.getSessionId());
        record.setKbIds(String.join(",", dto.getKbIds().stream().map(String::valueOf).toList()));
        record.setQuestion(dto.getQuestion());
        record.setAnswer(generationResponse.getContent());
        record.setSourceDocs(JSONUtil.toJsonStr(sourceDocs));
        record.setLatencyMs(latency);
        record.setIsStream(0);
        record.setCreatedAt(LocalDateTime.now());
        chatRecordMapper.insert(record);
        updateSessionAfterChat(dto.getSessionId());

        vo.setChatId(record.getId());

        // 7. 写入缓存 — 仅缓存有效结果（非空检索+非空回答）
        if (!sourceDocs.isEmpty() && StrUtil.isNotBlank(generationResponse.getContent())
                && generationResponse.getTokenCount() > 0) {
            stringRedisTemplate.opsForValue().set(cacheKey, JSONUtil.toJsonStr(vo),
                    Duration.ofSeconds(CacheConstants.QA_CACHE_TTL));
        }

        return Result.success(vo);
    }

    @Override
    public SseEmitter streamChat(QuestionDTO dto, Long userId) {
        SseEmitter emitter = new SseEmitter(120000L);

        // 异步处理
        Thread thread = new Thread(() -> {
            try {
                // 立即发送初始心跳，告知客户端连接已建立
                emitter.send(SseEmitter.event().name("ping").data("connected").build());

                // 1. gRPC检索
                RetrievalResponse retrievalResponse = retrievalClient.retrieve(
                        dto.getQuestion(), dto.getKbIds(), dto.getTopK(), dto.getScoreThreshold());

                List<String> contexts = new ArrayList<>();
                List<AnswerVO.SourceDoc> sourceDocs = new ArrayList<>();
                for (DocumentChunk chunk : retrievalResponse.getChunksList()) {
                    contexts.add(chunk.getContent());
                    AnswerVO.SourceDoc src = new AnswerVO.SourceDoc();
                    src.setDocumentId(chunk.getDocumentId());
                    src.setDocumentName(chunk.getDocumentName());
                    src.setChunkId(chunk.getChunkId());
                    src.setChunkIndex(chunk.getChunkIndex());
                    src.setContent(chunk.getContent());
                    src.setScore(chunk.getScore());
                    sourceDocs.add(src);
                }

                // 发送初始心跳
                emitter.send(SseEmitter.event().name("ping").data("").build());

                // 2. 流式生成，每收到token通过SSE发送
                StringBuilder fullAnswer = new StringBuilder();
                int[] totalTokens = {0};
                long startTime = System.currentTimeMillis();

                Iterator<GenerationResponse> stream = generationClient.generateStreamIterator(
                        dto.getQuestion(), contexts);

                long lastHeartbeat = System.currentTimeMillis();
                while (stream.hasNext()) {
                    GenerationResponse resp = stream.next();
                    fullAnswer.append(resp.getContent());
                    totalTokens[0] = resp.getTokenCount();

                    emitter.send(SseEmitter.event()
                            .data(resp.getContent())
                            .build());

                    // 每15秒发送心跳防止连接超时
                    long now = System.currentTimeMillis();
                    if (now - lastHeartbeat > 15000) {
                        emitter.send(SseEmitter.event().name("ping").data("").build());
                        lastHeartbeat = now;
                    }

                    if (resp.getIsEnd()) {
                        break;
                    }
                }

                // 3. 保存问答记录
                int latency = (int) (System.currentTimeMillis() - startTime);
                ChatRecord record = new ChatRecord();
                record.setUserId(userId);
                record.setSessionId(dto.getSessionId());
                record.setKbIds(String.join(",", dto.getKbIds().stream().map(String::valueOf).toList()));
                record.setQuestion(dto.getQuestion());
                record.setAnswer(fullAnswer.toString());
                record.setSourceDocs(JSONUtil.toJsonStr(sourceDocs));
                record.setLatencyMs(latency);
                record.setIsStream(1);
                record.setCreatedAt(LocalDateTime.now());
                chatRecordMapper.insert(record);
                updateSessionAfterChat(dto.getSessionId());

                // 发送结束事件（包含完整元数据和检索来源）
                emitter.send(SseEmitter.event()
                        .name("done")
                        .data("{\"chatId\":" + record.getId() + ",\"tokenCount\":" + totalTokens[0] + ",\"latencyMs\":" + latency + ",\"sourceDocs\":" + JSONUtil.toJsonStr(sourceDocs) + "}")
                        .build());

                emitter.complete();
            } catch (Exception e) {
                log.error("SSE流式问答异常", e);
                try {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data("问答服务异常，请稍后再试")
                            .build());
                } catch (Exception ignored) {
                }
                emitter.completeWithError(e);
            }
        });
        thread.setDaemon(true);
        thread.start();

        emitter.onCompletion(() -> {
            log.info("SSE连接完成: userId={}", userId);
            thread.interrupt();
        });
        emitter.onTimeout(() -> {
            log.warn("SSE连接超时: userId={}", userId);
            thread.interrupt();
        });

        return emitter;
    }

    @Override
    public Result<Page<ChatHistoryVO>> getHistory(Long userId, Integer page, Integer size) {
        Page<ChatRecord> pg = new Page<>(page, size);
        LambdaQueryWrapper<ChatRecord> wrapper = new LambdaQueryWrapper<ChatRecord>()
                .eq(ChatRecord::getUserId, userId)
                .orderByDesc(ChatRecord::getCreatedAt);

        Page<ChatRecord> result = chatRecordMapper.selectPage(pg, wrapper);
        Page<ChatHistoryVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(result.getRecords().stream().map(r -> {
            ChatHistoryVO vo = new ChatHistoryVO();
            vo.setId(r.getId());
            vo.setSessionId(r.getSessionId());
            vo.setQuestion(r.getQuestion());
            vo.setAnswer(r.getAnswer().length() > 200
                    ? r.getAnswer().substring(0, 200) + "..." : r.getAnswer());
            vo.setRating(r.getRating());
            vo.setLatencyMs(r.getLatencyMs());
            vo.setIsStream(r.getIsStream());
            vo.setCreatedAt(r.getCreatedAt());
            return vo;
        }).toList());
        return Result.success(voPage);
    }

    private void updateSessionAfterChat(Long sessionId) {
        if (sessionId == null) return;
        ChatSession session = sessionMapper.selectById(sessionId);
        if (session != null) {
            session.setMessageCount(session.getMessageCount() + 1);
            session.setUpdatedAt(LocalDateTime.now());
            sessionMapper.updateById(session);
        }
    }
}
