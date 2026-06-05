package com.rag.common.exception;

import com.rag.common.result.Result;
import com.rag.common.result.ResultCodeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<Void> handleAccessDenied(AccessDeniedException e) {
        log.warn("权限拒绝: {}", e.getMessage());
        return Result.fail(ResultCodeEnum.FORBIDDEN.getCode(), "无权限: " + e.getMessage());
    }

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleValidationException(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", msg);
        return Result.fail(ResultCodeEnum.PARAM_ERROR.getCode(), msg);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleHttpMessageNotReadable(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败: {}", e.getMessage());
        return Result.fail(ResultCodeEnum.PARAM_ERROR.getCode(), "请求参数格式错误，请检查JSON类型是否正确");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("非法参数: {}", e.getMessage());
        return Result.fail(ResultCodeEnum.PARAM_ERROR.getCode(), e.getMessage());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleMissingServletRequestParameter(MissingServletRequestParameterException e) {
        log.warn("缺少必要参数: {}", e.getMessage());
        return Result.fail(ResultCodeEnum.PARAM_ERROR.getCode(), "缺少必要参数: " + e.getParameterName());
    }

    @ExceptionHandler(org.springframework.dao.DataAccessException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleDataAccessException(org.springframework.dao.DataAccessException e) {
        log.error("数据库访问异常", e);
        return Result.fail(ResultCodeEnum.SYSTEM_ERROR.getCode(), "数据访问异常，请稍后重试");
    }

    @ExceptionHandler(java.io.IOException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleIOException(java.io.IOException e) {
        log.error("IO异常: {}", e.getMessage());
        return Result.fail(ResultCodeEnum.FILE_NOT_FOUND.getCode(), "文件读取失败: " + e.getMessage());
    }

    @ExceptionHandler(jakarta.servlet.ServletException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleServletException(jakarta.servlet.ServletException e) {
        log.error("Servlet异常: {}", e.getMessage());
        return Result.fail(ResultCodeEnum.SYSTEM_ERROR.getCode(), "请求处理异常");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return Result.fail(ResultCodeEnum.SYSTEM_ERROR);
    }
}
