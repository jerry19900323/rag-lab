package com.example.raglab.application;

import com.example.raglab.domain.DocumentChunk;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.stereotype.Service;

/** Produces a short file-level description for document-first retrieval. */
@Service
public class DocumentSummaryGenerator {
    private final ModelSelectionResolver models;
    private final ChatCompletionClient completion;

    public DocumentSummaryGenerator(ModelSelectionResolver models, ChatCompletionClient completion) {
        this.models = models;
        this.completion = completion;
    }

    public String summarize(DocumentProcessingResult document) {
        String sample = sample(document);
        ResolvedModel model = models.resolve(ModelPurpose.DOCUMENT_SUMMARY)
                .or(() -> models.resolve(ModelPurpose.ANSWER))
                .or(() -> models.resolve(ModelPurpose.CHAT))
                .orElse(null);
        if (model == null) return extractiveSummary(document);
        String summary = completion.summarizeDocument(model, document.fileName(), sample);
        return summary.substring(0, Math.min(2000, summary.length()));
    }

    private String sample(DocumentProcessingResult document) {
        var chunks = document.chunks();
        if (chunks.isEmpty()) throw new IllegalArgumentException("Document has no searchable content");
        Set<Integer> positions = new LinkedHashSet<>();
        positions.add(0);
        positions.add(chunks.size() / 2);
        positions.add(chunks.size() - 1);
        StringBuilder text = new StringBuilder();
        for (int position : positions) {
            DocumentChunk chunk = chunks.get(position);
            int length = Math.min(1400, chunk.content().length());
            text.append(chunk.content(), 0, length).append("\n\n");
        }
        return text.toString().trim();
    }

    private String extractiveSummary(DocumentProcessingResult document) {
        var chunks = document.chunks();
        Set<Integer> positions = new LinkedHashSet<>();
        positions.add(0);
        positions.add(chunks.size() / 2);
        positions.add(chunks.size() - 1);
        StringBuilder text = new StringBuilder(document.fileName()).append(": ");
        for (int position : positions) {
            String content = chunks.get(position).content().strip();
            text.append(content, 0, Math.min(350, content.length())).append(' ');
        }
        return text.toString().strip();
    }
}
