package com.example.raglab.infrastructure.document;

import org.springframework.web.multipart.MultipartFile;

/** Contract for converting an uploaded document into normalized text. */
public interface DocumentParser {

    /**
     * Parses and normalizes one uploaded document.
     *
     * @param file source document
     * @return normalized content and source metadata
     */
    ParsedDocument parse(MultipartFile file);
}
