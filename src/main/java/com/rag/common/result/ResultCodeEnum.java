package com.rag.common.result;

import lombok.Getter;

@Getter
public enum ResultCodeEnum implements ResultCode {

    SUCCESS(0, "成功"),
    PARAM_ERROR(400, "参数错误"),
    UNAUTHORIZED(401, "未授权"),
    FORBIDDEN(403, "无权限"),
    NOT_FOUND(404, "资源不存在"),
    METHOD_NOT_ALLOWED(405, "请求方法不支持"),
    CONFLICT(409, "资源冲突"),
    RATE_LIMIT(429, "请求过于频繁"),
    SYSTEM_ERROR(500, "系统内部错误"),
    SERVICE_UNAVAILABLE(503, "服务不可用"),

    // 业务错误码 (10000+)
    FILE_UPLOAD_ERROR(10001, "文件上传失败"),
    FILE_NOT_FOUND(10002, "文件不存在"),
    FILE_DUPLICATE(10003, "文件已存在"),
    FILE_TYPE_NOT_SUPPORTED(10004, "不支持的文件类型"),
    FILE_SIZE_EXCEEDED(10005, "文件大小超出限制"),

    DOCUMENT_NOT_FOUND(10101, "文档不存在"),
    DOCUMENT_STATUS_ERROR(10102, "文档状态异常"),

    KB_NOT_FOUND(10201, "知识库不存在"),
    KB_NAME_DUPLICATE(10202, "知识库名称已存在"),

    REVIEW_NOT_FOUND(10301, "审核记录不存在"),
    REVIEW_ALREADY_HANDLED(10302, "审核已处理"),
    REVIEW_PERMISSION_DENIED(10303, "无审核权限"),

    USER_NOT_FOUND(10401, "用户不存在"),
    USERNAME_DUPLICATE(10402, "用户名已存在"),
    PASSWORD_ERROR(10403, "密码错误"),
    TOKEN_EXPIRED(10404, "Token已过期"),
    TOKEN_INVALID(10405, "Token无效"),
    ACCOUNT_DISABLED(10406, "账号已被禁用"),

    QA_SERVICE_ERROR(10501, "问答服务异常"),
    QA_RETRIEVAL_ERROR(10502, "检索服务异常"),
    QA_GENERATION_ERROR(10503, "生成服务异常"),

    GRPC_CALL_ERROR(10601, "gRPC调用失败"),
    KAFKA_SEND_ERROR(10602, "Kafka消息发送失败");

    private final int code;
    private final String message;

    ResultCodeEnum(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
