package com.ariza.agent.core;

import com.ariza.agent.core.guardrail.Guardrail;
import com.ariza.agent.core.tool.Tool;

import java.util.List;
import java.util.Objects;

/**
 * @author ariza
 */
public record Agent(
        String name,
        String instructions,
        String model,
        List<Tool> tools,
        List<Handoff> handoffs,
        List<Guardrail> guardrails) {

    /**
     * 创建智能体配置，并将工具、任务移交和护栏列表转换为不可变副本。
     *
     * @param name         智能体名称
     * @param instructions 智能体遵循的系统指令
     * @param model        使用的模型名称
     * @param tools        智能体可调用的工具列表，传入 {@code null} 时使用空列表
     * @param handoffs     可移交任务的目标列表，传入 {@code null} 时使用空列表
     * @param guardrails   输入或输出护栏列表，传入 {@code null} 时使用空列表
     * @throws NullPointerException 当名称、指令或模型为 {@code null} 时抛出
     */
    public Agent {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(instructions, "instructions");
        Objects.requireNonNull(model, "model");
        tools = tools == null ? List.of() : List.copyOf(tools);
        handoffs = handoffs == null ? List.of() : List.copyOf(handoffs);
        guardrails = guardrails == null ? List.of() : List.copyOf(guardrails);
    }

    /**
     * 创建用于逐项配置智能体的构建器。
     *
     * @return 新的智能体构建器
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * @author ariza
     */
    public static final class Builder {
        private String name;
        private String instructions;
        private String model;
        private List<Tool> tools = List.of();
        private List<Handoff> handoffs = List.of();
        private List<Guardrail> guardrails = List.of();

        /**
         * 设置智能体名称。
         *
         * @param value 智能体名称
         * @return 当前构建器
         */
        public Builder name(String value) {
            name = value;
            return this;
        }

        /**
         * 设置智能体遵循的系统指令。
         *
         * @param value 系统指令
         * @return 当前构建器
         */
        public Builder instructions(String value) {
            instructions = value;
            return this;
        }

        /**
         * 设置智能体使用的模型名称。
         *
         * @param value 模型名称
         * @return 当前构建器
         */
        public Builder model(String value) {
            model = value;
            return this;
        }

        /**
         * 设置智能体可调用的工具列表。
         *
         * @param value 工具列表
         * @return 当前构建器
         */
        public Builder tools(List<Tool> value) {
            tools = value;
            return this;
        }

        /**
         * 设置智能体可用的任务移交列表。
         *
         * @param value 任务移交列表
         * @return 当前构建器
         */
        public Builder handoffs(List<Handoff> value) {
            handoffs = value;
            return this;
        }

        /**
         * 设置智能体使用的护栏列表。
         *
         * @param value 护栏列表
         * @return 当前构建器
         */
        public Builder guardrails(List<Guardrail> value) {
            guardrails = value;
            return this;
        }

        /**
         * 根据当前配置创建智能体。
         *
         * @return 配置完成的智能体
         * @throws NullPointerException 当名称、指令或模型尚未设置时抛出
         */
        public Agent build() {
            return new Agent(name, instructions, model, tools, handoffs, guardrails);
        }
    }
}
