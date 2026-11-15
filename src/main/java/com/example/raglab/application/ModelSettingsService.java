package com.example.raglab.application;

import com.example.raglab.infrastructure.auth.ModelSecretCipher;
import com.example.raglab.infrastructure.web.ModelCatalogClient;
import java.time.Instant;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Administrator-managed model connections and role assignments. */
@Service
public class ModelSettingsService {

    private final ModelConnectionStore store;
    private final ModelSecretCipher cipher;
    private final ModelCatalogClient catalog;

    public ModelSettingsService(ModelConnectionStore store, ModelSecretCipher cipher, ModelCatalogClient catalog) {
        this.store = store;
        this.cipher = cipher;
        this.catalog = catalog;
    }

    public List<ModelProviderView> providers() {
        return Arrays.stream(ModelProvider.values())
                .map(provider -> new ModelProviderView(provider, provider.displayName(),
                        provider.requiresApiKey(), provider.supportedPurposes()))
                .toList();
    }

    public List<ModelUsageTypeView> usageTypes() {
        return Arrays.stream(ModelPurpose.values())
                .map(purpose -> new ModelUsageTypeView(purpose, purpose.displayName()))
                .toList();
    }

    public List<ModelConnectionView> connections() {
        return store.listConnections().stream().map(connection -> view(connection,
                connection.encryptedApiKey() == null ? null : cipher.decrypt(connection.encryptedApiKey())))
                .toList();
    }

    @Transactional
    public ModelConnectionView create(ModelConnectionInput input) {
        validateInput(input);
        validateCloudflareAccount(input.provider(), input.accountId());
        if (input.provider() == ModelProvider.CLOUDFLARE
                && (input.usageType() == null || input.modelId() == null || input.modelId().isBlank())) {
            throw new IllegalArgumentException("Cloudflare usageType and modelId are required");
        }
        if ((input.usageType() == null) != (input.modelId() == null || input.modelId().isBlank())) {
            throw new IllegalArgumentException("usageType and modelId must both be set");
        }
        if (input.modelId() != null && !input.modelId().isBlank()) {
            if (input.modelId().trim().length() > 200) {
                throw new IllegalArgumentException("modelId must not exceed 200 characters");
            }
            if (Boolean.FALSE.equals(input.enabled())) {
                throw new IllegalArgumentException("Cannot assign a model to a disabled connection");
            }
            if (!input.provider().supports(input.usageType())) {
                throw new IllegalArgumentException("The provider does not support this model purpose");
            }
        }
        String encrypted = credential(input.provider(), input.apiKey(), null);
        Instant now = Instant.now();
        ModelConnection connection = new ModelConnection(UUID.randomUUID().toString(),
                input.name().trim(), input.provider(), encrypted,
                input.enabled() == null || input.enabled(), now, now,
                normalizeAccountId(input.accountId()), normalizeApiBaseUrl(input.apiBaseUrl()));
        store.saveConnection(connection);
        if (input.usageType() != null) {
            store.saveAssignment(new ModelAssignment(input.usageType(), connection.connectionId(),
                    input.modelId().trim(), now));
        }
        return view(connection, input.apiKey() == null ? null : input.apiKey().trim());
    }

    public ModelConnectionView update(String id, ModelConnectionInput input) {
        ModelConnection old = find(id);
        validateInput(input);
        validateCloudflareAccount(input.provider(), input.accountId() == null ? old.accountId() : input.accountId());
        if (input.provider() != old.provider()) {
            throw new IllegalArgumentException("provider cannot be changed; create another connection");
        }
        String encrypted = credential(input.provider(), input.apiKey(), old.encryptedApiKey());
        ModelConnection connection = new ModelConnection(id, input.name().trim(), input.provider(),
                encrypted, input.enabled() == null ? old.enabled() : input.enabled(),
                old.createdAt(), Instant.now(),
                input.accountId() == null ? old.accountId() : normalizeAccountId(input.accountId()),
                input.apiBaseUrl() == null ? old.apiBaseUrl() : normalizeApiBaseUrl(input.apiBaseUrl()));
        store.saveConnection(connection);
        String apiKey = input.apiKey() != null && !input.apiKey().isBlank()
                ? input.apiKey().trim()
                : connection.encryptedApiKey() == null ? null : cipher.decrypt(connection.encryptedApiKey());
        return view(connection, apiKey);
    }

    private ModelConnectionView view(ModelConnection connection, String apiKey) {
        return new ModelConnectionView(connection.connectionId(), connection.name(), connection.provider(),
                connection.enabled(), connection.encryptedApiKey() != null, connection.createdAt(),
                connection.updatedAt(), connection.accountId(), connection.apiBaseUrl(), apiKey);
    }

    public void delete(String id) {
        find(id);
        try {
            store.deleteConnection(id);
        } catch (DataAccessException exception) {
            throw new ModelConnectionDeleteException(exception);
        }
    }

    public List<ModelOption> models(String id) {
        ModelConnection connection = find(id);
        String apiKey = connection.encryptedApiKey() == null ? null : cipher.decrypt(connection.encryptedApiKey());
        return catalog.listModels(connection.provider(), apiKey, connection.apiBaseUrl(), connection.accountId());
    }

    public ModelConnectionTestResult test(String id) {
        List<ModelOption> models = models(id);
        return new ModelConnectionTestResult("UP", models.size());
    }

    public List<ModelAssignment> assignments() {
        List<ModelAssignment> stored = store.listAssignments();
        return Arrays.stream(ModelPurpose.values())
                .map(purpose -> stored.stream().filter(item -> item.purpose() == purpose).findFirst()
                        .orElse(new ModelAssignment(purpose, null, null, null)))
                .toList();
    }

    public ModelAssignment assign(ModelPurpose purpose, ModelAssignmentInput input) {
        if (purpose == null || input == null) {
            throw new IllegalArgumentException("Model purpose and assignment are required");
        }
        String id = input.connectionId();
        String modelId = input.modelId();
        if (id == null && modelId == null) {
            ModelAssignment disabled = new ModelAssignment(purpose, null, null, Instant.now());
            store.saveAssignment(disabled);
            return disabled;
        }
        if (id == null || id.isBlank() || modelId == null || modelId.isBlank()) {
            throw new IllegalArgumentException("connectionId and modelId must both be set or both be null");
        }
        modelId = modelId.trim();
        if (modelId.length() > 200) {
            throw new IllegalArgumentException("modelId must not exceed 200 characters");
        }
        ModelConnection connection = find(id);
        if (!connection.enabled()) {
            throw new IllegalArgumentException("The model connection is disabled");
        }
        if (!connection.provider().supports(purpose)) {
            throw new IllegalArgumentException("The provider does not support this model purpose");
        }
        ModelAssignment assignment = new ModelAssignment(purpose, id, modelId, Instant.now());
        store.saveAssignment(assignment);
        return assignment;
    }

    private ModelConnection find(String id) {
        return store.findConnection(id).orElseThrow(() -> new ModelConnectionNotFoundException(id));
    }

    private void validateInput(ModelConnectionInput input) {
        if (input == null || input.name() == null || input.name().isBlank()
                || input.name().trim().length() > 100) {
            throw new IllegalArgumentException("name must contain 1 to 100 characters");
        }
        if (input.provider() == null) {
            throw new IllegalArgumentException("provider is required");
        }
    }

    private void validateCloudflareAccount(ModelProvider provider, String accountId) {
        if (provider == ModelProvider.CLOUDFLARE
                && (accountId == null || !accountId.trim().matches("[A-Za-z0-9_-]{1,200}"))) {
            throw new IllegalArgumentException("Cloudflare accountId is required and must contain only letters, digits, _ or -");
        }
    }

    private String normalizeAccountId(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > 200) throw new IllegalArgumentException("accountId must not exceed 200 characters");
        return normalized;
    }

    private String normalizeApiBaseUrl(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim().replaceAll("/+$", "");
        if (normalized.length() > 500) throw new IllegalArgumentException("apiBaseUrl must not exceed 500 characters");
        try {
            URI uri = URI.create(normalized);
            if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getRawUserInfo() != null
                    || uri.getRawQuery() != null || uri.getRawFragment() != null) {
                throw new IllegalArgumentException("apiBaseUrl must be an HTTP(S) base URL without credentials or query");
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("apiBaseUrl must be a valid HTTP(S) base URL", exception);
        }
        return normalized;
    }

    private String credential(ModelProvider provider, String supplied, String existing) {
        if (!provider.requiresApiKey()) {
            return null;
        }
        if (supplied != null && !supplied.isBlank()) {
            return cipher.encrypt(supplied.trim());
        }
        if (existing != null) {
            return existing;
        }
        throw new IllegalArgumentException("apiKey is required for this provider");
    }

    public record ModelProviderView(
            ModelProvider provider, String displayName, boolean requiresApiKey,
            List<ModelPurpose> supportedPurposes) {
    }

    public record ModelConnectionTestResult(String status, int discoveredModelCount) {
    }

}
