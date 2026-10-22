package com.example.raglab.application;

import com.example.raglab.domain.ChunkingOptions;
import com.example.raglab.domain.DocumentChunk;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** Produces deterministic, traceable chunks while preferring natural text boundaries. */
@Component
public class StableTextChunker implements DocumentChunker {

    /**
     * Splits text with stable offsets and deterministic overlap.
     * Boundary priority is paragraph, line, sentence, and then whitespace.
     */
    @Override
    public List<DocumentChunk> chunk(String text, ChunkingOptions options) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }

        List<DocumentChunk> chunks = new ArrayList<>();
        int start = skipLeadingWhitespace(text, 0);
        while (start < text.length()) {
            int hardEnd = Math.min(start + options.maxCharacters(), text.length());
            int end = hardEnd == text.length()
                    ? hardEnd
                    : findPreferredBoundary(text, start, hardEnd, options.maxCharacters());
            end = trimTrailingWhitespace(text, start, end);

            // A pathological whitespace region must still make forward progress.
            if (end <= start) {
                end = hardEnd;
            }

            String content = text.substring(start, end);
            chunks.add(new DocumentChunk(chunks.size(), start, end, content.length(), content));
            if (end >= text.length()) {
                break;
            }

            int nextStart = Math.max(start + 1, end - options.overlapCharacters());
            nextStart = skipLeadingWhitespace(text, nextStart);
            start = Math.min(nextStart, text.length());
        }
        return List.copyOf(chunks);
    }

    private int findPreferredBoundary(String text, int start, int hardEnd, int maxCharacters) {
        // Avoid creating an unnecessarily small chunk just to use an early boundary.
        int minimumBoundary = start + Math.max(1, maxCharacters / 2);
        int boundary = lastBoundary(text, "\n\n", minimumBoundary, hardEnd, 2);
        if (boundary > start) {
            return boundary;
        }
        boundary = lastBoundary(text, "\n", minimumBoundary, hardEnd, 1);
        if (boundary > start) {
            return boundary;
        }
        boundary = lastSentenceBoundary(text, minimumBoundary, hardEnd);
        if (boundary > start) {
            return boundary;
        }
        for (int index = hardEnd; index >= minimumBoundary; index--) {
            if (Character.isWhitespace(text.charAt(index - 1))) {
                return index;
            }
        }
        return hardEnd;
    }

    private int lastBoundary(String text, String delimiter, int minimum, int hardEnd, int delimiterLength) {
        int position = text.lastIndexOf(delimiter, hardEnd - delimiterLength);
        return position >= minimum ? position + delimiterLength : -1;
    }

    private int lastSentenceBoundary(String text, int minimum, int hardEnd) {
        for (int index = hardEnd - 1; index >= minimum; index--) {
            char current = text.charAt(index);
            if (current == '.' || current == '!' || current == '?' || current == '。'
                    || current == '！' || current == '？') {
                return index + 1;
            }
        }
        return -1;
    }

    private int skipLeadingWhitespace(String text, int offset) {
        int result = offset;
        while (result < text.length() && Character.isWhitespace(text.charAt(result))) {
            result++;
        }
        return result;
    }

    private int trimTrailingWhitespace(String text, int start, int end) {
        int result = end;
        while (result > start && Character.isWhitespace(text.charAt(result - 1))) {
            result--;
        }
        return result;
    }
}
