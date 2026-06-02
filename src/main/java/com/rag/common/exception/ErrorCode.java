package com.rag.common.exception;

import com.rag.common.result.ResultCode;

/**
 * 业务错误码枚举，与 ResultCodeEnum 区分，用于业务异常的额外错误码场景。
 * 一般情况直接使用 ResultCodeEnum。
 */
public interface ErrorCode extends ResultCode {
}
