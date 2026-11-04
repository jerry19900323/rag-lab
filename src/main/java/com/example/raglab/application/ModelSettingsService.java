package com.example.raglab.application;

import com.example.raglab.infrastructure.auth.ModelSecretCipher;
import com.example.raglab.infrastructure.web.ModelCatalogClient;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

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
        return store.listConnections().stream().map(ModelConnection::view).toList();
    }

    public ModelConnectionView create(ModelConnectionInput input) {
        validateInput(input);
        String encrypted = credential(input.provider(), input.apiKey(), null);
        Instant now = Instant.now();
        ModelConnection connection = new ModelConnection(UUID.randomUUID().toString(),
                input.name().trim(), input.provider(), encrypted,
                input.enabled() == null || input.enabled(), now, now);
        store.saveConnection(connection);
        return connection.view();
    }

    public ModelConnectionView update(String id, ModelConnectionInput input) {
        ModelConnection old = find(id);
        validateInput(input);
        if (input.provider() != old.provider()) {
            throw new IllegalArgumentException("provider cannot be changed; create another connection");
        }
        String encrypted = credential(input.provider(), input.apiKey(), old.encryptedApiKey());
        ModelConnection connection = new ModelConnection(id, input.name().trim(), input.provider(),
                encrypted, input.enabled() == null ? old.enabled() : input.enabled(),
                old.createdAt(), Instant.now());
        store.saveConnection(connection);
        return connection.view();
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
        return catalog.listModels(connection.provider(), apiKey);
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
