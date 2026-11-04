package com.example.raglab.api;

import com.example.raglab.application.DocumentIndexingResult;
import com.example.raglab.application.DocumentIndexingService;
import com.example.raglab.domain.ChunkingOptions;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Exposes durable document ingestion operations. */
@RestController
@RequestMapping("/api/documents")
public class DocumentIndexController {

    private final DocumentIndexingService indexingService;

    public DocumentIndexController(DocumentIndexingService indexingService) {
        this.indexingService = indexingService;
    }

    /** Parses, chunks, and commits a new generation of an uploaded document. */
    @PostMapping(value = "/index", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentIndexingResult> index(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String documentId,
            @RequestParam(defaultValue = "" + ChunkingOptions.DEFAULT_MAX_CHARACTERS) int maxCharacters,
            @RequestParam(defaultValue = "" + ChunkingOptions.DEFAULT_OVERLAP_CHARACTERS) int overlapCharacters) {
        DocumentIndexingResult result = indexingService.index(
                file, documentId, maxCharacters, overlapCharacters);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }
}
