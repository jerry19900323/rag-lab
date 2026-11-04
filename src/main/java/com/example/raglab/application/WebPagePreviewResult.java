package com.example.raglab.application;

import java.util.List;

/** Page discovery metadata and chunk preview for one web import. */
public record WebPagePreviewResult(
        String requestedUrl,
        boolean paginationFollowed,
        List<WebPageInfo> pages,
        DocumentProcessingResult document) {

    public WebPagePreviewResult {
        pages = List.copyOf(pages);
    }
}
