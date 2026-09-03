package com.ariza.agent.tracing;

import com.ariza.agent.core.tracing.Span;
import com.ariza.agent.core.tracing.TraceEvent;
import com.ariza.agent.core.tracing.TraceExporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author ariza
 */
public final class LogTraceExporter implements TraceExporter {
    private static final Logger log = LoggerFactory.getLogger(LogTraceExporter.class);

    /**
     * 将追踪跨度以结构化参数形式写入应用日志。
     *
     * @param span 需要导出的追踪跨度
     */
    @Override
    public void export(Span span) {
        log.info("agent span: {}", span);
    }

    /**
     * 将有序轨迹事件写入应用日志。
     *
     * @param event 需要导出的轨迹事件
     */
    @Override
    public void exportEvent(TraceEvent event) {
        log.info("agent event: {}", event);
    }
}
