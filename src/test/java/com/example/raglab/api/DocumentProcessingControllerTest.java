package com.example.raglab.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.raglab.application.DocumentProcessingService;
import com.example.raglab.application.StableTextChunker;
import com.example.raglab.infrastructure.document.TextDocumentParser;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DocumentProcessingController.class)
@Import({DocumentProcessingService.class, StableTextChunker.class, TextDocumentParser.class, ApiExceptionHandler.class})
class DocumentProcessingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    /** Verifies the complete upload, parse, chunk, and response workflow. */
    @Test
    void shouldPreviewMarkdownChunks() throws Exception {
        String content = "# First\n\n" + "A".repeat(80) + "\n\n# Second\n\n" + "B".repeat(80);
        var file = new MockMultipartFile(
                "file", "notes.md", "text/markdown", content.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/documents/preview")
                        .file(file)
                        .param("maxCharacters", "100")
                        .param("overlapCharacters", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileName").value("notes.md"))
                .andExpect(jsonPath("$.documentType").value("MARKDOWN"))
                .andExpect(jsonPath("$.chunkCount").value(2))
                .andExpect(jsonPath("$.chunks[0].index").value(0))
                .andExpect(jsonPath("$.chunks[1].index").value(1));
    }

    /** Verifies that invalid chunk settings produce a useful client error. */
    @Test
    void shouldRejectInvalidChunkSettings() throws Exception {
        var file = new MockMultipartFile("file", "notes.txt", "text/plain", "Readable text".getBytes());

        mockMvc.perform(multipart("/api/documents/preview")
                        .file(file)
                        .param("maxCharacters", "100")
                        .param("overlapCharacters", "100"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(
                        "overlapCharacters must be non-negative and smaller than maxCharacters"));
    }
}
