package com.example.raglab.infrastructure.web;

import java.net.URI;

/** Downloads one validated public HTML page. */
public interface WebPageClient {

    FetchedWebPage fetch(URI uri);
}
