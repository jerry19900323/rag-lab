package com.example.raglab.api;

import com.example.raglab.application.DocumentProcessingResult;
import com.example.raglab.application.DocumentProcessingService;
import com.example.raglab.domain.ChunkingOptions;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Exposes document parsing and chunk-preview operations. */
@RestController
@RequestMapping("/api/documents")
public class DocumentProcessingController {

    private final DocumentProcessingService service;

    public DocumentProcessingController(DocumentProcessingService service) {
        this.service = service;
    }

    /** Parses and chunks a TXT or Markdown upload for immediate inspection. */
    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DocumentProcessingResult preview(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "" + ChunkingOptions.DEFAULT_MAX_CHARACTERS) int maxCharacters,
            @RequestParam(defaultValue = "" + ChunkingOptions.DEFAULT_OVERLAP_CHARACTERS) int overlapCharacters) {
        return service.preview(file, maxCharacters, overlapCharacters);
    }
}
