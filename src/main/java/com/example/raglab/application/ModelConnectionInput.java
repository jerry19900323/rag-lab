package com.example.raglab.application;

/** Create/update form. A null API key on update keeps the current key. */
public record ModelConnectionInput(String name, ModelProvider provider, String apiKey, Boolean enabled) {
}
