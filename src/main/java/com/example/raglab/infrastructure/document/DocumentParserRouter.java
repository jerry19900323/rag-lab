package com.example.raglab.infrastructure.document;

import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/** Selects a format-specific parser from the uploaded file extension. */
@Component
public class DocumentParserRouter implements DocumentParser {

    private final List<DocumentFormatParser> parsers;

    public DocumentParserRouter(List<DocumentFormatParser> parsers) {
        this.parsers = List.copyOf(parsers);
    }

    @Override
    public ParsedDocument parse(MultipartFile file) {
        String fileName = sanitizeFileName(file.getOriginalFilename());
        if (file.isEmpty()) {
            throw new IllegalArgumentException("The uploaded document is empty");
        }
        String extension = extensionOf(fileName);
        return parsers.stream()
                .filter(parser -> parser.supports(extension))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Only .txt, .md, .markdown, .pdf, .docx, .pptx, .xlsx, .html, and .htm files are supported"))
                .parse(file, fileName);
    }

    static String normalizeText(String content) {
        return content.replace("\r\n", "\n").replace('\r', '\n');
    }

    private String sanitizeFileName(String originalFileName) {
        if (originalFileName == null || originalFileName.isBlank()) {
            throw new IllegalArgumentException("The uploaded document must have a file name");
        }
        String normalized = originalFileName.replace('\\', '/');
        String fileName = normalized.substring(normalized.lastIndexOf('/') + 1);
        if (fileName.isBlank()) {
            throw new IllegalArgumentException("The uploaded document must have a file name");
        }
        return fileName;
    }

    private String extensionOf(String fileName) {
        int separator = fileName.lastIndexOf('.');
        return separator < 0 ? "" : fileName.substring(separator + 1).toLowerCase(Locale.ROOT);
    }
}
