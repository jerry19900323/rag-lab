package com.example.raglab.infrastructure.document;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/** Parses UTF-8 TXT and Markdown files into normalized text. */
@Component
public class TextDocumentParser implements DocumentParser {

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("txt", "md", "markdown");

    /**
     * Validates the file type, decodes UTF-8 strictly, and normalizes line endings.
     * Strict decoding prevents silently corrupted document content.
     */
    @Override
    public ParsedDocument parse(MultipartFile file) {
        String fileName = sanitizeFileName(file.getOriginalFilename());
        String extension = extensionOf(fileName);
        if (!SUPPORTED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("Only .txt, .md, and .markdown files are supported");
        }
        if (file.isEmpty()) {
            throw new IllegalArgumentException("The uploaded document is empty");
        }

        String content;
        try {
            var decoder = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            content = decoder.decode(ByteBuffer.wrap(file.getBytes())).toString();
        } catch (CharacterCodingException exception) {
            throw new IllegalArgumentException("The document must use valid UTF-8 encoding", exception);
        } catch (Exception exception) {
            throw new IllegalArgumentException("The document could not be read", exception);
        }

        // A UTF-8 byte order mark is content metadata, not part of the document text.
        if (!content.isEmpty() && content.charAt(0) == '\uFEFF') {
            content = content.substring(1);
        }
        content = content.replace("\r\n", "\n").replace('\r', '\n');
        if (content.isBlank()) {
            throw new IllegalArgumentException("The uploaded document does not contain readable text");
        }

        String documentType = extension.equals("txt") ? "TEXT" : "MARKDOWN";
        return new ParsedDocument(fileName, documentType, content);
    }

    private String sanitizeFileName(String originalFileName) {
        if (originalFileName == null || originalFileName.isBlank()) {
            throw new IllegalArgumentException("The uploaded document must have a file name");
        }
        String normalized = originalFileName.replace('\\', '/');
        return normalized.substring(normalized.lastIndexOf('/') + 1);
    }

    private String extensionOf(String fileName) {
        int separator = fileName.lastIndexOf('.');
        return separator < 0 ? "" : fileName.substring(separator + 1).toLowerCase(Locale.ROOT);
    }
}
