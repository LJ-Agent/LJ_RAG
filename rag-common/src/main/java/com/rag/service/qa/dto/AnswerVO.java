package com.rag.service.qa.dto;

import lombok.Data;

import java.util.List;

@Data
public class AnswerVO {

    private Long chatId;
    private String answer;
    private List<SourceDoc> sourceDocs;
    private Integer tokenCount;
    private Integer latencyMs;

    @Data
    public static class SourceDoc {
        private Long documentId;
        private String documentName;
        private String chunkId;
        private Integer chunkIndex;
        private String content;
        private Float score;
    }
}
