package com.ariza.agent.tool.gdal;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 将用户输入的 GDAL 命令行解析为可执行参数，并校验命令是否属于允许执行的 GDAL 工具白名单。
 *
 * @author ariza
 */
public final class GdalCommandParser {

    /**
     * 允许执行的 GDAL/OGR 命令白名单，对应 alpine gdal-tools 提供的全部工具。
     */
    static final Set<String> ALLOWED_TOOLS = Set.of(
            "gdal2tiles", "gdal2tiles.py",
            "gdal2xyz", "gdal2xyz.py",
            "gdal_calc", "gdal_calc.py",
            "gdal_contour",
            "gdal_create",
            "gdal_edit", "gdal_edit.py",
            "gdal_fillnodata", "gdal_fillnodata.py",
            "gdal_footprint",
            "gdal_grid",
            "gdal_merge", "gdal_merge.py",
            "gdal_pansharpen", "gdal_pansharpen.py",
            "gdal_polygonize", "gdal_polygonize.py",
            "gdal_proximity", "gdal_proximity.py",
            "gdal_rasterize",
            "gdal_retile", "gdal_retile.py",
            "gdal_sieve", "gdal_sieve.py",
            "gdal_translate",
            "gdal_viewshed",
            "gdaladdo",
            "gdalattachpct", "gdalattachpct.py",
            "gdalbuildvrt",
            "gdalcompare", "gdalcompare.py",
            "gdaldem",
            "gdalenhance",
            "gdalinfo",
            "gdallocationinfo",
            "gdalmanage",
            "gdalmdiminfo",
            "gdalmdimtranslate",
            "gdalmove", "gdalmove.py",
            "gdalsrsinfo",
            "gdaltindex",
            "gdaltransform",
            "gdalwarp",
            "ogr2ogr",
            "ogr_layer_algebra", "ogr_layer_algebra.py",
            "ogrinfo",
            "ogrlineref",
            "ogrmerge", "ogrmerge.py",
            "ogrtindex"
    );

    private static final String[] META_CHARS = {"`", ";", "|", "&", "$", "\n"};

    private GdalCommandParser() {
    }

    /**
     * 解析并校验命令行。
     *
     * <p>命令按 POSIX shell 词法拆分为参数，支持单引号、双引号和反斜杠转义；工具名必须属于白名单，
     * 参数中不允许出现 shell 元字符。解析得到的参数将直接以 argv 形式执行，不经过任何 shell。</p>
     *
     * @param command 用户输入的完整命令行，例如 {@code gdalinfo /data/sample.tif}
     * @return 解析结果；校验失败时返回 {@code null}
     */
    public static GdalCommand parse(String command) {
        if (command == null || command.isBlank()) {
            return null;
        }
        List<String> tokens = tokenize(command.trim());
        if (tokens.isEmpty() || !ALLOWED_TOOLS.contains(tokens.get(0))) {
            return null;
        }
        for (int index = 1; index < tokens.size(); index++) {
            if (containsMetaChar(tokens.get(index))) {
                return null;
            }
        }
        return new GdalCommand(tokens.get(0), List.copyOf(tokens.subList(1, tokens.size())));
    }

    /**
     * 按 POSIX shell 词法将命令拆分为参数列表。
     *
     * @param command 原始命令行
     * @return 参数列表；引号未闭合时返回空列表
     */
    static List<String> tokenize(String command) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inToken = false;
        char quote = 0;

        for (int index = 0; index < command.length(); index++) {
            char ch = command.charAt(index);
            if (quote != 0) {
                if (ch == quote) {
                    quote = 0;
                } else if (ch == '\\' && quote == '"') {
                    if (index + 1 < command.length()) {
                        current.append(command.charAt(++index));
                    }
                } else {
                    current.append(ch);
                }
                continue;
            }
            if (ch == '\'' || ch == '"') {
                quote = ch;
                inToken = true;
                continue;
            }
            if (ch == '\\') {
                if (index + 1 < command.length()) {
                    current.append(command.charAt(++index));
                }
                inToken = true;
                continue;
            }
            if (ch == ' ' || ch == '\t') {
                if (inToken) {
                    tokens.add(current.toString());
                    current.setLength(0);
                    inToken = false;
                }
                continue;
            }
            current.append(ch);
            inToken = true;
        }
        if (quote != 0) {
            return List.of();
        }
        if (inToken) {
            tokens.add(current.toString());
        }
        return tokens;
    }

    /**
     * 判断参数中是否包含 shell 元字符。
     */
    private static boolean containsMetaChar(String token) {
        for (String meta : META_CHARS) {
            if (token.contains(meta)) {
                return true;
            }
        }
        return false;
    }
}