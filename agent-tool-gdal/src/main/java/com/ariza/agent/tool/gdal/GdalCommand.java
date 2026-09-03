package com.ariza.agent.tool.gdal;

import java.util.List;

/**
 * 解析校验通过的 GDAL 命令行。
 *
 * @author ariza
 */
public record GdalCommand(String tool, List<String> args) {
}