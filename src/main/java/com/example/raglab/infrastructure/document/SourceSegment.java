package com.example.raglab.infrastructure.document;

/** Source structure associated with a range in normalized document text. */
public record SourceSegment(
        int startOffset,
        int endOffset,
        Integer pageNumber,
        Integer slideNumber,
        String sheetName,
        String sectionTitle,
        String elementType,
        String sourceUrl,
        String sourceTitle,
        Integer webPageNumber) {

    public SourceSegment(
            int startOffset,
            int endOffset,
            Integer pageNumber,
            Integer slideNumber,
            String sheetName,
            String sectionTitle,
            String elementType) {
        this(startOffset, endOffset, pageNumber, slideNumber, sheetName, sectionTitle,
                elementType, null, null, null);
    }

    public SourceSegment {
        if (startOffset < 0 || endOffset < startOffset) {
            throw new IllegalArgumentException("Source segment offsets are invalid");
        }
    }
}
