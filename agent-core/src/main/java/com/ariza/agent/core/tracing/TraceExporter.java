package com.ariza.agent.core.tracing;

/**
 * @author ariza
 */
@FunctionalInterface
public interface TraceExporter {

    /**
     * 将指定追踪跨度发送到目标存储或输出通道。
     *
     * @param span 需要导出的追踪跨度
     */
    void export(Span span);

    /**
     * 导出一条有序轨迹事件。默认实现保持现有 Span 导出器兼容。
     *
     * @param event 需要导出的轨迹事件
     */
    default void exportEvent(TraceEvent event) {
    }
}
