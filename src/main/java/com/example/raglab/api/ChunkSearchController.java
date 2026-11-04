package com.example.raglab.api;

import com.example.raglab.application.ChunkSearchHit;
import com.example.raglab.application.ChunkSearchRequest;
import com.example.raglab.application.ChunkSearchService;
import com.example.raglab.application.PagedResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Exposes ranked chunk retrieval to authenticated clients. */
@RestController
@RequestMapping("/api/search")
public class ChunkSearchController {

    private final ChunkSearchService service;

    public ChunkSearchController(ChunkSearchService service) {
        this.service = service;
    }

    @GetMapping
    public PagedResult<ChunkSearchHit> search(
            @RequestParam String query,
            @RequestParam(required = false) String documentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.search(new ChunkSearchRequest(query, documentId, page, size));
    }
}
