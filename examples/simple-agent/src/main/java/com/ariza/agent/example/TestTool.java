package com.ariza.agent.example;

import com.ariza.agent.core.RunContext;
import com.ariza.agent.tool.reflect.AgentTool;
import com.ariza.agent.tool.reflect.ToolParam;
import com.ariza.agent.tool.reflect.ToolResultField;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * 一个测试 agent 工具类
 *
 * @author ariza
 * @since 2026-08-07 17:24:36
 */
@Component
public class TestTool {

    @AgentTool(name = "get_news", description = "这是一个获取新闻列表,返回的是新闻标题")
    public List<News> listNews(
            @ToolParam(value = "title", description = "标题", required = false) String title) {
        return Arrays.asList(new News("新闻1", ""), new News("新闻2", ""));
    }

    @AgentTool(name = "news_detail", description = "这是一个获取新闻的详细信息,在用户没有问你的时候不要主动查询")
    public String detail(
            @ToolParam(value = "title", description = "标题") String title) {
        return "这是新闻详情==========>" + title;
    }

    @AgentTool(name = "get_today", description = "获取今天的日期")
    public String today(RunContext runContext) {
        System.out.println("runContext:" + runContext.attributes());
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        return sdf.format(new Date());
    }

//    @AgentTool(name = "run_cmd", description = "可以执行命令行脚本")
//    public String runCmd(
//            @ToolParam(value = "cmd_content", description = "命令内容") String[] cmd) throws IOException {
//        if (cmd == null || cmd.length == 0 || cmd[0] == null || cmd[0].isBlank()) {
//            throw new IllegalArgumentException("命令内容不能为空");
//        }
//        if (Arrays.stream(cmd).anyMatch(argument -> argument == null)) {
//            throw new IllegalArgumentException("命令参数不能为 null");
//        }
//
//        Process process = new ProcessBuilder(Arrays.copyOf(cmd, cmd.length))
//                .redirectErrorStream(true)
//                .start();
//        try (InputStream outputStream = process.getInputStream()) {
//            String output = new String(outputStream.readAllBytes(), Charset.defaultCharset());
//            int exitCode = process.waitFor();
//            if (exitCode == 0) {
//                return output;
//            }
//            String result = "命令执行失败，退出码: " + exitCode;
//            return output.isBlank() ? result : result + System.lineSeparator() + output;
//        } catch (InterruptedException exception) {
//            process.destroyForcibly();
//            Thread.currentThread().interrupt();
//            throw new IOException("等待命令执行时线程被中断", exception);
//        } catch (IOException exception) {
//            process.destroyForcibly();
//            throw exception;
//        }
//    }


    /**
     * @author ariza
     */
    public class News {
        @ToolResultField(description = "标题", hasValue = true)
        private String title;
        @ToolResultField(description = "内容")
        private String content;

        public News() {
        }

        public News(String title, String content) {
            this.title = title;
            this.content = content;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }
    }


}
