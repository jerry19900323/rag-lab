package com.example.raglab.infrastructure.elasticsearch;

import co.elastic.clients.elasticsearch._types.mapping.DynamicMapping;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.PutMappingRequest;

/** Versioned Elasticsearch index definition for searchable document chunks. */
public final class RagChunkIndexDefinition {

    public static final String INDEX_NAME = "rag_chunks_v1";
    public static final String INDEX_ALIAS = "rag_chunks";

    private RagChunkIndexDefinition() {
    }

    /** Builds the complete create-index request, including its stable alias. */
    public static CreateIndexRequest createRequest() {
        return new CreateIndexRequest.Builder()
                .index(INDEX_NAME)
                .aliases(INDEX_ALIAS, alias -> alias)
                .mappings(mapping())
                .build();
    }

    static TypeMapping mapping() {
        return new TypeMapping.Builder()
                .dynamic(DynamicMapping.Strict)
                .properties("documentId", property -> property.keyword(keyword -> keyword))
                .properties("ingestionId", property -> property.keyword(keyword -> keyword))
                .properties("fileName", property -> property.keyword(keyword -> keyword))
                .properties("documentType", property -> property.keyword(keyword -> keyword))
                .properties("documentHash", property -> property.keyword(keyword -> keyword))
                .properties("chunkIndex", property -> property.integer(integer -> integer))
                .properties("content", property -> property.text(text -> text))
                .properties("characterCount", property -> property.integer(integer -> integer))
                .properties("startOffset", property -> property.integer(integer -> integer))
                .properties("endOffset", property -> property.integer(integer -> integer))
                .properties("contentHash", property -> property.keyword(keyword -> keyword))
                .properties("indexedAt", property -> property.date(date -> date))
                .properties("schemaVersion", property -> property.keyword(keyword -> keyword))
                .properties("sourceReferences", property -> property.nested(nested -> nested
                        .properties("pageNumber", field -> field.integer(integer -> integer))
                        .properties("slideNumber", field -> field.integer(integer -> integer))
                        .properties("sheetName", field -> field.keyword(keyword -> keyword))
                        .properties("sectionTitle", field -> field.keyword(keyword -> keyword))
                        .properties("elementType", field -> field.keyword(keyword -> keyword))
                        .properties("sourceUrl", field -> field.keyword(keyword -> keyword
                                .ignoreAbove(2048)))
                        .properties("sourceTitle", field -> field.keyword(keyword -> keyword
                                .ignoreAbove(1024)))
                        .properties("webPageNumber", field -> field.integer(integer -> integer))))
                .build();
    }

    /** Builds an additive mapping update for an index created by an older application build. */
    public static PutMappingRequest updateMappingRequest() {
        return new PutMappingRequest.Builder()
                .index(INDEX_NAME)
                .properties(mapping().properties())
                .build();
    }
}
