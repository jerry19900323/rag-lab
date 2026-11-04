package com.example.raglab.infrastructure.document;

import org.springframework.web.multipart.MultipartFile;

/** Parser dedicated to one or more document file extensions. */
public interface DocumentFormatParser {

    boolean supports(String extension);

    ParsedDocument parse(MultipartFile file, String sanitizedFileName);
}
