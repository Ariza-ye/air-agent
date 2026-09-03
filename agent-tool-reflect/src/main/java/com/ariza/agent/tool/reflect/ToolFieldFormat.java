package com.ariza.agent.tool.reflect;

/**
 * 工具返回字段支持的格式。
 *
 * <p>格式名称参照 JSON Schema 和 OpenAPI 的常用约定。</p>
 *
 * @author ariza
 */
public enum ToolFieldFormat {

    UNSPECIFIED("", "", ""),
    DATE("date", "LocalDate", "2026-08-07"),
    DATE_TIME("date-time", "LocalDateTime、OffsetDateTime", "2026-08-07T10:30:00+08:00"),
    TIME("time", "LocalTime", "10:30:00"),
    URI("uri", "URI 或网址字符串", "https://example.com/news/1"),
    EMAIL("email", "邮箱字符串", "user@example.com"),
    IPV4("ipv4", "IPv4 地址字符串", "192.168.1.1"),
    IPV6("ipv6", "IPv6 地址字符串", "2001:db8::1"),
    UUID("uuid", "UUID", "550e8400-e29b-41d4-a716-446655440000"),
    HOSTNAME("hostname", "主机名字符串", "example.com"),
    DURATION("duration", "持续时间字符串", "PT30M"),
    INT32("int32", "Integer", "123"),
    INT64("int64", "Long", "123456789"),
    FLOAT("float", "Float", "1.25"),
    DOUBLE("double", "Double", "1.25"),
    BYTE("byte", "Base64 编码字符串", "SGVsbG8="),
    BINARY("binary", "二进制内容", "文件内容");

    private final String value;
    private final String javaType; // java 类型
    private final String example;  // 示例

    ToolFieldFormat(String value, String javaType, String example) {
        this.value = value;
        this.javaType = javaType;
        this.example = example;
    }


    /**
     * 获取写入工具字段结构的格式名称。
     *
     * @return 格式名称；未指定格式时返回空字符串
     */
    public String value() {
        return value;
    }

    /**
     * 获取适合此格式的 Java 类型或用途说明。
     *
     * @return Java 类型或用途；未指定格式时返回空字符串
     */
    public String javaType() {
        return javaType;
    }

    /**
     * 获取此格式的示例值。
     *
     * @return 示例值；未指定格式时返回空字符串
     */
    public String example() {
        return example;
    }

}
