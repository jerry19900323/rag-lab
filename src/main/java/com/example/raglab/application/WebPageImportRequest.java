package com.example.raglab.application;

import com.example.raglab.domain.ChunkingOptions;
import jakarta.validation.constraints.NotBlank;

/** Options shared by web-page preview and durable indexing. */
public record WebPageImportRequest(
        @NotBlank String url,
        Boolean followPagination,
        Integer maxPages,
        String documentId,
        Integer maxCharacters,
        Integer overlapCharacters) {

    public WebPageImportRequest {
        followPagination = followPagination == null ? Boolean.FALSE : followPagination;
        maxPages = maxPages == null ? 10 : maxPages;
        maxCharacters = maxCharacters == null
                ? ChunkingOptions.DEFAULT_MAX_CHARACTERS : maxCharacters;
        overlapCharacters = overlapCharacters == null
                ? ChunkingOptions.DEFAULT_OVERLAP_CHARACTERS : overlapCharacters;
    }
}
