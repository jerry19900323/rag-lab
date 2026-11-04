package com.example.raglab.application;

import java.util.List;

/** Page discovery metadata and durable indexing result for one web import. */
public record WebPageIndexingResult(
        String requestedUrl,
        boolean paginationFollowed,
        List<WebPageInfo> pages,
        DocumentIndexingResult document) {

    public WebPageIndexingResult {
        pages = List.copyOf(pages);
    }
}
