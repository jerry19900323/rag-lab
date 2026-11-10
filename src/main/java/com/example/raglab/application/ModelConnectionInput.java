package com.example.raglab.application;

/** Create/update form. A null API key on update keeps the current key. */
public record ModelConnectionInput(String name, ModelProvider provider, String apiKey, Boolean enabled,
        String accountId, String apiBaseUrl, ModelPurpose usageType, String modelId) {
    public ModelConnectionInput(String name, ModelProvider provider, String apiKey, Boolean enabled) {
        this(name, provider, apiKey, enabled, null, null, null, null);
    }

    public ModelConnectionInput(String name, ModelProvider provider, String apiKey, Boolean enabled,
            String accountId, String apiBaseUrl) {
        this(name, provider, apiKey, enabled, accountId, apiBaseUrl, null, null);
    }
}
