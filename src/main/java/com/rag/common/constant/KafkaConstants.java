package com.rag.common.constant;

public interface KafkaConstants {

    /** 文件处理任务主题（Java -> Python） */
    String TOPIC_FILE_PROCESS = "rag-file-process";

    /** 分块处理任务主题（Java -> Python） */
    String TOPIC_CHUNK_PROCESS = "rag-chunk-process";

    /** 向量化入库任务主题（Java -> Python） */
    String TOPIC_EMBED_PROCESS = "rag-embed-process";

    /** 文档删除通知主题（Java -> Python） */
    String TOPIC_DOCUMENT_DELETE = "rag-document-delete";

    /** 任务完成通知主题（Python -> Java） */
    String TOPIC_TASK_COMPLETE = "rag-task-complete";

    /** 任务失败通知主题（Python -> Java） */
    String TOPIC_TASK_FAILED = "rag-task-failed";

    /** 消费者组ID */
    String CONSUMER_GROUP = "rag-server-group";
}
