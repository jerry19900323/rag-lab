package com.example.raglab.application;

import java.util.UUID;
import org.springframework.stereotype.Component;

/** Generates opaque identifiers for logical documents and ingestion generations. */
@Component
public class DocumentIdentifierGenerator {

    public String newDocumentId() {
        return UUID.randomUUID().toString();
    }

    public String newIngestionId() {
        return UUID.randomUUID().toString();
    }
}
