package com.example.knowledgeagent.document.parser.impl;

import com.example.knowledgeagent.document.enums.FileType;
import org.springframework.stereotype.Component;

@Component
/**
 * 定义 MarkdownDocumentParser 组件，承载对应模块的业务职责。
 */
public class MarkdownDocumentParser extends TxtDocumentParser {
    /**
     * Markdown 当前按纯文本读取，保留原始标题、列表和代码块内容。
     */
    @Override
    public boolean supports(FileType fileType) {
        return FileType.MD == fileType;
    }
}
