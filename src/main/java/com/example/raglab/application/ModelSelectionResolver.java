package com.example.raglab.application;

import com.example.raglab.infrastructure.auth.ModelSecretCipher;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Resolves the enabled model selected for a specific application scenario. */
@Service
public class ModelSelectionResolver {

    private final ModelConnectionStore store;
    private final ModelSecretCipher cipher;

    public ModelSelectionResolver(ModelConnectionStore store, ModelSecretCipher cipher) {
        this.store = store;
        this.cipher = cipher;
    }

    public Optional<ResolvedModel> resolve(ModelPurpose usageType) {
        if (usageType == null) {
            throw new IllegalArgumentException("usageType is required");
        }
        return store.findAssignment(usageType)
                .filter(assignment -> assignment.connectionId() != null && assignment.modelId() != null)
                .flatMap(assignment -> store.findConnection(assignment.connectionId())
                        .filter(ModelConnection::enabled)
                        .map(connection -> new ResolvedModel(
                                usageType, connection.provider(),
                                assignment.modelId(), connection.encryptedApiKey() == null
                                        ? null : cipher.decrypt(connection.encryptedApiKey()))));
    }
}
