package com.rag.infrastructure.mybatis.interceptor;

import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;

import java.sql.Statement;

/**
 * SQL性能日志拦截器，记录慢SQL。
 */
@Slf4j
@Intercepts({
        @Signature(type = StatementHandler.class, method = "query", args = {Statement.class, ResultHandler.class}),
        @Signature(type = StatementHandler.class, method = "update", args = {Statement.class})
})
public class SqlLogInterceptor implements Interceptor {

    private static final long SLOW_SQL_THRESHOLD = 1000L;

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        long start = System.currentTimeMillis();
        try {
            return invocation.proceed();
        } finally {
            long elapsed = System.currentTimeMillis() - start;
            if (elapsed > SLOW_SQL_THRESHOLD) {
                StatementHandler handler = (StatementHandler) invocation.getTarget();
                String sql = handler.getBoundSql().getSql();
                log.warn("慢SQL [{}ms]: {}", elapsed, sql);
            }
        }
    }
}
