package com.example.raglab.api;

import com.example.raglab.application.DocumentQueryService;
import com.example.raglab.application.DocumentSummary;
import com.example.raglab.application.IngestionSummary;
import com.example.raglab.application.PagedResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Exposes document metadata and ingestion history for management clients. */
@RestController
@RequestMapping("/api/documents")
public class DocumentQueryController {

    private final DocumentQueryService queryService;

    public DocumentQueryController(DocumentQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    public PagedResult<DocumentSummary> listDocuments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String document) {
        return queryService.listDocuments(page, size, document);
    }

    @GetMapping("/{documentId}")
    public DocumentSummary getDocument(@PathVariable String documentId) {
        return queryService.getDocument(documentId);
    }

    @GetMapping("/{documentId}/ingestions")
    public PagedResult<IngestionSummary> listIngestions(
            @PathVariable String documentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return queryService.listIngestions(documentId, page, size);
    }
}
