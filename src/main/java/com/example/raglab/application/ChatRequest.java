package com.example.raglab.application;

public record ChatRequest(String question, String documentId, Integer topK,
        Double temperature, Integer maxTokens, ChatMode mode, String conversationId) {
    public ChatRequest(String question, String documentId, Integer topK,
            Double temperature, Integer maxTokens) {
        this(question, documentId, topK, temperature, maxTokens, null, null);
    }

    public ChatRequest(String question, String documentId, Integer topK,
            Double temperature, Integer maxTokens, ChatMode mode) {
        this(question, documentId, topK, temperature, maxTokens, mode, null);
    }

    public ChatRequest {
        if (question == null || question.isBlank() || question.trim().length() > 2000)
            throw new IllegalArgumentException("question must contain 1 to 2000 characters");
        question = question.trim();
        if (documentId != null) {
            documentId = documentId.trim();
            if (documentId.isEmpty()) documentId = null;
            else if (documentId.length() > 200)
                throw new IllegalArgumentException("documentId must not exceed 200 characters");
        }
        if (topK == null) topK = 5;
        if (topK < 1 || topK > 10) throw new IllegalArgumentException("topK must be between 1 and 10");
        if (temperature == null) temperature = 0.2;
        if (!Double.isFinite(temperature) || temperature < 0 || temperature > 2)
            throw new IllegalArgumentException("temperature must be between 0 and 2");
        if (maxTokens == null) maxTokens = 1024;
        if (maxTokens < 1 || maxTokens > 4096)
            throw new IllegalArgumentException("maxTokens must be between 1 and 4096");
        if (mode == null) mode = ChatMode.DOCUMENT;
        if (conversationId != null) {
            conversationId = conversationId.trim();
            try { java.util.UUID.fromString(conversationId); }
            catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("conversationId must be a UUID");
            }
        }
    }
}
