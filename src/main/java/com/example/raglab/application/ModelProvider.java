package com.example.raglab.application;

import java.util.Arrays;
import java.util.List;

/** Supported connection presets and their known role capabilities. */
public enum ModelProvider {
    OPENAI("OpenAI", "https://api.openai.com/v1/models", true, true, true),
    DEEPSEEK("DeepSeek", "https://api.deepseek.com/models", true, false, true),
    DASHSCOPE("Alibaba Cloud Model Studio (international)", "https://dashscope-intl.aliyuncs.com/api/v1/models?page_size=100", true, true, true),
    ANTHROPIC("Claude", "https://api.anthropic.com/v1/models?limit=100", true, false, true),
    GEMINI("Gemini", "https://generativelanguage.googleapis.com/v1beta/models?pageSize=1000", true, true, true),
    OLLAMA("Ollama (local)", "http://127.0.0.1:11434/api/tags", true, true, false);

    private final String displayName;
    private final String catalogUrl;
    private final boolean chat;
    private final boolean embedding;
    private final boolean requiresApiKey;

    ModelProvider(String displayName, String catalogUrl, boolean chat, boolean embedding, boolean requiresApiKey) {
        this.displayName = displayName;
        this.catalogUrl = catalogUrl;
        this.chat = chat;
        this.embedding = embedding;
        this.requiresApiKey = requiresApiKey;
    }

    public String displayName() { return displayName; }
    public String catalogUrl() { return catalogUrl; }
    public boolean requiresApiKey() { return requiresApiKey; }

    public List<ModelPurpose> supportedPurposes() {
        return Arrays.stream(ModelPurpose.values())
                .filter(purpose -> switch (purpose) {
                    case ANSWER, CHAT, ROUTING, QUERY_REWRITE, DOCUMENT_SUMMARY,
                            INFORMATION_EXTRACTION, TRANSLATION, EMBEDDING -> true;
                    default -> false;
                })
                .filter(this::supports)
                .toList();
    }

    public boolean supports(ModelPurpose purpose) {
        return switch (purpose) {
            case EMBEDDING -> embedding;
            case ANSWER, CHAT, ROUTING, QUERY_REWRITE, DOCUMENT_SUMMARY,
                    INFORMATION_EXTRACTION, TRANSLATION -> chat;
            default -> true;
        };
    }
}
