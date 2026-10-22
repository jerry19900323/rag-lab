package com.example.raglab.infrastructure.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class TextDocumentParserTest {

    private final TextDocumentParser parser = new TextDocumentParser();

    /** Verifies BOM removal and platform-independent line normalization. */
    @Test
    void shouldNormalizeUtf8Markdown() {
        byte[] content = ("\uFEFF# Title\r\n\r\nBody").getBytes(StandardCharsets.UTF_8);
        var file = new MockMultipartFile("file", "guide.md", "text/markdown", content);

        ParsedDocument document = parser.parse(file);

        assertThat(document.documentType()).isEqualTo("MARKDOWN");
        assertThat(document.content()).isEqualTo("# Title\n\nBody");
    }

    /** Verifies that file content is never inferred from an unsupported extension. */
    @Test
    void shouldRejectUnsupportedFiles() {
        var file = new MockMultipartFile("file", "guide.pdf", "application/pdf", "data".getBytes());

        assertThatThrownBy(() -> parser.parse(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only .txt, .md, and .markdown");
    }
}
