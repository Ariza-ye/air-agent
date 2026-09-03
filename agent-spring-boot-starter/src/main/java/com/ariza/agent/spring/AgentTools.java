package com.ariza.agent.spring;

import com.ariza.agent.core.tool.Tool;

import java.util.List;


/**
 * agent 工具集
 *
 * @author ariza
 */
public class AgentTools {

    List<Tool> tools;

    public AgentTools() {
    }

    public AgentTools(List<Tool> tools) {
        this.tools = tools;
    }

    public List<Tool> getTools() {
        return tools;
    }

    public void setTools(List<Tool> tools) {
        this.tools = tools;
    }
}
