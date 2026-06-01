package com.rag.infrastructure.security;

import com.rag.common.context.UserContext;
import com.rag.common.exception.BusinessException;
import com.rag.common.result.ResultCodeEnum;
import com.rag.domain.entity.Document;
import com.rag.domain.entity.KnowledgeBase;
import com.rag.domain.mapper.DocumentMapper;
import com.rag.domain.mapper.KnowledgeBaseMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Parameter;

/**
 * 团队权限校验 AOP 切面。
 *
 * 拦截 @TeamPermission 注解的方法，校验：
 * 1. 垂直越权：当前用户角色是否有该权限码
 * 2. 水平越权：当前用户是否属于目标资源（KB/文档）的团队
 *
 * 超级管理员 (GLOBAL:ADMIN 或 CONFIG:MANAGE) 跳过团队检查。
 */
@Aspect
@Component
@Order(1) // 在 @Transactional 之前执行
@RequiredArgsConstructor
@Slf4j
public class TeamPermissionAspect {

    private final KnowledgeBaseMapper kbMapper;
    private final DocumentMapper documentMapper;
    private final HttpServletRequest request;

    @Before("@annotation(perm)")
    public void checkPermission(JoinPoint joinPoint, TeamPermission perm) {
        Long userId = UserContext.getUserId();
        String requiredPerm = perm.value();

        // ─── 1. 垂直越权检查：是否有该权限码 ──────
        if (!UserContext.hasPermission(requiredPerm)) {
            // 超级管理员拥有所有权限
            if (!UserContext.hasPermission("CONFIG:MANAGE")) {
                log.warn("垂直越权拦截: userId={}, required={}, has={}", userId, requiredPerm,
                        UserContext.getPermissions());
                throw new BusinessException(ResultCodeEnum.FORBIDDEN.getCode(),
                        "无权限: " + requiredPerm + " (403 Vertical)");
            }
        }

        // ─── 2. 水平越权检查：用户 ∈ 资源所属团队 ──────
        if (UserContext.hasPermission("CONFIG:MANAGE")) {
            return; // 超级管理员跳过团队检查
        }

        Long resourceKbId = resolveKbId(joinPoint, perm);
        if (resourceKbId == null) {
            return; // 无法解析 kbId 时放行（后续业务层自行校验）
        }

        // 查询 KB 归属团队
        KnowledgeBase kb = kbMapper.selectById(resourceKbId);
        if (kb == null) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR.getCode(), "知识库不存在");
        }

        // 查询用户在该团队的成员信息 — 需走 team_members 表
        // 当前阶段: 以 owner_id 作为简化的团队归属检查
        Long kbOwnerId = kb.getOwnerId();
        boolean isOwner = kbOwnerId != null && kbOwnerId.equals(userId);

        if (!isOwner) {
            log.warn("水平越权拦截: userId={}, kbId={}, kbOwner={}, required={}",
                    userId, resourceKbId, kbOwnerId, requiredPerm);
            throw new BusinessException(ResultCodeEnum.FORBIDDEN.getCode(),
                    "无权访问该资源 (403 Horizontal)");
        }
    }

    /**
     * 从方法参数中解析目标知识库 ID。
     *
     * KB 级别：直接取 kbIdParam 参数
     * DOCUMENT 级别：取 docIdParam → 查 document → 返回 document.kbId
     */
    private Long resolveKbId(JoinPoint joinPoint, TeamPermission perm) {
        MethodSignature sig = (MethodSignature) joinPoint.getSignature();
        Object[] args = joinPoint.getArgs();
        Parameter[] params = sig.getMethod().getParameters();

        if (perm.resourceType() == TeamPermission.ResourceType.KB) {
            return getParamValue(args, params, perm.kbIdParam());
        }

        if (perm.resourceType() == TeamPermission.ResourceType.DOCUMENT) {
            Long docId = getParamValue(args, params, perm.docIdParam());
            if (docId == null) return null;
            Document doc = documentMapper.selectById(docId);
            return doc != null ? doc.getKbId() : null;
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    private <T> T getParamValue(Object[] args, Parameter[] params, String paramName) {
        for (int i = 0; i < params.length; i++) {
            // 从 @RequestParam 或 @PathVariable 取值
            String name = paramName;
            if (params[i].getName().equals(name) || hasAnnotationName(params[i], name)) {
                return (T) args[i];
            }
        }
        // 如果参数名不匹配，尝试从请求中直接取
        String reqVal = request.getParameter(paramName);
        if (reqVal != null) {
            try { return (T) Long.valueOf(reqVal); } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    private boolean hasAnnotationName(Parameter param, String name) {
        if (param.isAnnotationPresent(org.springframework.web.bind.annotation.RequestParam.class)) {
            String val = param.getAnnotation(org.springframework.web.bind.annotation.RequestParam.class).value();
            if (val.equals(name) || (val.isEmpty() && param.getName().equals(name))) return true;
        }
        if (param.isAnnotationPresent(org.springframework.web.bind.annotation.PathVariable.class)) {
            String val = param.getAnnotation(org.springframework.web.bind.annotation.PathVariable.class).value();
            if (val.equals(name) || (val.isEmpty() && param.getName().equals(name))) return true;
        }
        return false;
    }
}
