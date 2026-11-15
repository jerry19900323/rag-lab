package com.example.raglab.application;

/** Validated routing output used by retrieval and diagnostics. */
public record ChatRoutingDecision(ChatRoute route, String searchQuery, String reason, String documentHint) {
    public ChatRoutingDecision {
        if (route == null) throw new IllegalArgumentException("route is required");
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("routing reason is required");
        reason = reason.trim();
        if (reason.length() > 500) reason = reason.substring(0, 500);
        if (searchQuery != null) {
            searchQuery = searchQuery.trim();
            if (searchQuery.isEmpty()) searchQuery = null;
            else if (searchQuery.length() > 500) searchQuery = searchQuery.substring(0, 500);
        }
        if (documentHint != null) {
            documentHint = documentHint.trim();
            if (documentHint.isEmpty()) documentHint = null;
            else if (documentHint.length() > 200) documentHint = documentHint.substring(0, 200);
        }
        if (route.usesDocuments() && searchQuery == null)
            throw new IllegalArgumentException("document route needs searchQuery");
    }
}
