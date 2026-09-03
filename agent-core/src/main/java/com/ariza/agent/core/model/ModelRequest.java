package com.ariza.agent.core.model;

import com.ariza.agent.core.tool.Tool;

import java.util.List;
import java.util.Objects;

/**
 * @author ariza
 */
public record ModelRequest(String model,
                           String instructions,
                           List<ModelInputItem> input,
                           List<Tool> tools,
                           ModelContinuation continuation) {
    /**
     * 创建模型请求，并将工具列表转换为不可变副本。
     *
     * @param model        使用的模型名称
     * @param instructions 模型遵循的系统指令
     * @param input        本轮发送给模型的输入项
     * @param tools        模型可调用的工具列表，传入 {@code null} 时使用空列表
     * @param continuation 模型后续请求所需的续传信息，首次请求时为 {@code null}
     */
    public ModelRequest {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(instructions, "instructions");
        input = input == null ? List.of() : List.copyOf(input);
        tools = tools == null ? List.of() : List.copyOf(tools);
    }

    /**
     * 创建仅包含用户文本的模型请求。
     *
     * @param model        使用的模型名称
     * @param instructions 模型遵循的系统指令
     * @param input        用户输入文本
     * @param tools        模型可调用的工具列表
     * @param continuation 模型后续请求所需的续传信息
     */
    public ModelRequest(String model,
                        String instructions,
                        String input,
                        List<Tool> tools,
                        ModelContinuation continuation) {
        this(model,
                instructions,
                List.of(new UserInput(Objects.requireNonNull(input, "input"))),
                tools,
                continuation);
    }
}
