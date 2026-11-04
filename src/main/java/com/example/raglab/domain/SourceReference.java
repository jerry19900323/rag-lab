package com.example.raglab.domain;

/** Human-meaningful source location overlapping one generated chunk. */
public record SourceReference(
        Integer pageNumber,
        Integer slideNumber,
        String sheetName,
        String sectionTitle,
        String elementType,
        String sourceUrl,
        String sourceTitle,
        Integer webPageNumber) {

    public SourceReference(
            Integer pageNumber,
            Integer slideNumber,
            String sheetName,
            String sectionTitle,
            String elementType) {
        this(pageNumber, slideNumber, sheetName, sectionTitle, elementType, null, null, null);
    }
}
