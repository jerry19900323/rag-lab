package com.example.raglab.api;

import com.example.raglab.application.DocumentIndexingConflictException;
import com.example.raglab.application.DocumentDeletionConflictException;
import com.example.raglab.application.DocumentMetadataDeleteException;
import com.example.raglab.application.AdminRequiredException;
import com.example.raglab.application.ModelCatalogException;
import com.example.raglab.application.ModelConfigurationUnavailableException;
import com.example.raglab.application.ModelConnectionNotFoundException;
import com.example.raglab.application.ModelConnectionDeleteException;
import com.example.raglab.application.DocumentNotFoundException;
import com.example.raglab.application.InvalidCaptchaException;
import com.example.raglab.application.InvalidCredentialsException;
import com.example.raglab.infrastructure.elasticsearch.ChunkStoreException;
import com.example.raglab.infrastructure.elasticsearch.ChunkDeleteException;
import com.example.raglab.infrastructure.elasticsearch.ChunkSearchException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Converts expected input failures into consistent client-facing responses. */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(AdminRequiredException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Map<String, String> handleAdminRequired(AdminRequiredException exception) {
        return Map.of("error", exception.getMessage());
    }

    @ExceptionHandler(ModelConnectionNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> handleModelConnectionNotFound(ModelConnectionNotFoundException exception) {
        return Map.of("error", exception.getMessage());
    }

    @ExceptionHandler(ModelConnectionDeleteException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Map<String, String> handleModelConnectionDeleteFailure(ModelConnectionDeleteException exception) {
        return Map.of("error", "The model connection could not be deleted; retry the request");
    }

    @ExceptionHandler(ModelCatalogException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public Map<String, String> handleModelCatalogFailure(ModelCatalogException exception) {
        return Map.of("error", "The provider model list could not be loaded");
    }

    @ExceptionHandler(ModelConfigurationUnavailableException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Map<String, String> handleModelConfigurationUnavailable(ModelConfigurationUnavailableException exception) {
        return Map.of("error", exception.getMessage());
    }

    /** Reports document validation failures as HTTP 400 responses. */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleInvalidInput(IllegalArgumentException exception) {
        return Map.of("error", exception.getMessage());
    }

    /** Reports downstream indexing failures without exposing Elasticsearch internals. */
    @ExceptionHandler(ChunkStoreException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public Map<String, String> handleChunkStoreFailure(ChunkStoreException exception) {
        return Map.of("error", "The document could not be stored in the search index");
    }

    @ExceptionHandler(ChunkDeleteException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public Map<String, String> handleChunkDeleteFailure(ChunkDeleteException exception) {
        return Map.of("error", "The document could not be deleted from the search index");
    }

    @ExceptionHandler(DocumentMetadataDeleteException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Map<String, String> handleDocumentMetadataDeleteFailure(DocumentMetadataDeleteException exception) {
        return Map.of("error", "The document metadata could not be deleted; retry the request");
    }

    @ExceptionHandler(ChunkSearchException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public Map<String, String> handleChunkSearchFailure(ChunkSearchException exception) {
        return Map.of("error", "The search index could not be queried");
    }

    /** Reports concurrent updates of the same logical document as a conflict. */
    @ExceptionHandler(DocumentIndexingConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> handleIndexingConflict(DocumentIndexingConflictException exception) {
        return Map.of("error", exception.getMessage());
    }

    @ExceptionHandler(DocumentDeletionConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> handleDeletionConflict(DocumentDeletionConflictException exception) {
        return Map.of("error", exception.getMessage());
    }

    /** Reports unknown logical document identifiers. */
    @ExceptionHandler(DocumentNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> handleDocumentNotFound(DocumentNotFoundException exception) {
        return Map.of("error", exception.getMessage());
    }

    @ExceptionHandler(InvalidCaptchaException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleInvalidCaptcha(InvalidCaptchaException exception) {
        return Map.of("error", exception.getMessage());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Map<String, String> handleInvalidCredentials(InvalidCredentialsException exception) {
        return Map.of("error", exception.getMessage());
    }
}
