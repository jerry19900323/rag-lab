package com.example.raglab.infrastructure.web;

import java.net.URI;

/** Bounded HTML response returned by the public web client. */
public record FetchedWebPage(URI uri, byte[] body, String contentType) {

    public FetchedWebPage {
        body = body.clone();
    }
}
