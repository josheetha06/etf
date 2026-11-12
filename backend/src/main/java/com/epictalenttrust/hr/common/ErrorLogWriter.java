package com.epictalenttrust.hr.common;

import com.epictalenttrust.hr.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.io.PrintWriter;
import java.io.StringWriter;

/** Records unexpected failures in error_log. Never throws. */
@Component
public class ErrorLogWriter {

    private static final Logger log = LoggerFactory.getLogger(ErrorLogWriter.class);

    private final JdbcClient jdbc;

    public ErrorLogWriter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void record(String severity, String source, Throwable error, String contextJson) {
        try {
            StringWriter trace = new StringWriter();
            error.printStackTrace(new PrintWriter(trace));
            jdbc.sql("""
                    insert into error_log (severity, source, message, stack_trace, context, request_id, user_id)
                    values (:severity, :source, :message, :trace, cast(:context as jsonb), :requestId, :userId)
                    """)
                    .param("severity", severity)
                    .param("source", source)
                    .param("message", String.valueOf(error.getMessage()))
                    .param("trace", trace.toString())
                    .param("context", contextJson)
                    .param("requestId", RequestIdFilter.current())
                    .param("userId", CurrentUser.idOrNull())
                    .update();
        } catch (RuntimeException failure) {
            log.warn("Could not write error_log entry", failure);
        }
    }
}
