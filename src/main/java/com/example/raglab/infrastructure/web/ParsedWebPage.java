package com.example.raglab.infrastructure.web;

import com.example.raglab.infrastructure.document.ParsedDocument;
import java.net.URI;

/** Extracted readable content and optional next-page link. */
public record ParsedWebPage(URI uri, String title, ParsedDocument document, URI nextPage) {
}
