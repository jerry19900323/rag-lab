package com.example.raglab.application;

/** Internal runtime selection. Credentials must never be returned by an API controller. */
public record ResolvedModel(
        ModelPurpose usageType,
        ModelProvider provider,
        String modelId,
        String apiKey,
        String accountId,
        String apiBaseUrl) {

    @Override
    public String toString() {
        return "ResolvedModel[usageType=" + usageType + ", provider=" + provider
                + ", modelId=" + modelId + ", apiKey=<redacted>, accountId=" + accountId
                + ", apiBaseUrl=" + apiBaseUrl + "]";
    }
}
