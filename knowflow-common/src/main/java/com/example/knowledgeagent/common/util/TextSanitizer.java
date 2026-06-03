package com.example.knowledgeagent.common.util;

public final class TextSanitizer {
    private TextSanitizer() {
    }

    /**
     * 删除或清理 removeNullBytes 对应的业务资源。
     */
    public static String removeNullBytes(String value) {
        return value == null ? null : value.replace("\u0000", "");
    }
}
