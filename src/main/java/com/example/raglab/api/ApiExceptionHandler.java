package com.example.raglab.api;

import com.example.raglab.application.DocumentIndexingConflictException;
import com.example.raglab.application.DocumentNotFoundException;
import com.example.raglab.application.InvalidCaptchaException;
import com.example.raglab.application.InvalidCredentialsException;
import com.example.raglab.infrastructure.elasticsearch.ChunkStoreException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Converts expected input failures into consistent client-facing responses. */
@RestControllerAdvice
public class ApiExceptionHandler {

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

    /** Reports concurrent updates of the same logical document as a conflict. */
    @ExceptionHandler(DocumentIndexingConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> handleIndexingConflict(DocumentIndexingConflictException exception) {
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
