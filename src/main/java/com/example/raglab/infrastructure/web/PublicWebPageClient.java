package com.example.raglab.infrastructure.web;

import java.io.IOException;
import java.io.InputStream;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** HTTP client that rejects private destinations, unsafe redirects, and oversized responses. */
@Component
public class PublicWebPageClient implements WebPageClient {

    private static final int MAX_REDIRECTS = 5;

    private final HttpClient httpClient;
    private final Duration requestTimeout;
    private final int maximumBytes;

    public PublicWebPageClient(
            @Value("${rag.web.connect-timeout:5s}") Duration connectTimeout,
            @Value("${rag.web.request-timeout:15s}") Duration requestTimeout,
            @Value("${rag.web.max-response-bytes:2097152}") int maximumBytes) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        this.requestTimeout = requestTimeout;
        this.maximumBytes = maximumBytes;
    }

    @Override
    public FetchedWebPage fetch(URI requestedUri) {
        URI current = normalizeAndValidate(requestedUri);
        for (int redirect = 0; redirect <= MAX_REDIRECTS; redirect++) {
            HttpResponse<InputStream> response = send(current);
            int status = response.statusCode();
            if (status >= 300 && status < 400) {
                close(response.body());
                if (redirect == MAX_REDIRECTS) {
                    throw new IllegalArgumentException("The web page redirected too many times");
                }
                String location = response.headers().firstValue("location")
                        .orElseThrow(() -> new IllegalArgumentException(
                                "The web page returned a redirect without a location"));
                current = normalizeAndValidate(current.resolve(location));
                continue;
            }
            if (status < 200 || status >= 300) {
                close(response.body());
                throw new IllegalArgumentException("The web page returned HTTP status " + status);
            }
            String contentType = response.headers().firstValue("content-type").orElse("");
            if (!contentType.toLowerCase(Locale.ROOT).contains("text/html")) {
                close(response.body());
                throw new IllegalArgumentException("The URL must return HTML content");
            }
            long declaredLength = response.headers().firstValueAsLong("content-length").orElse(-1);
            if (declaredLength > maximumBytes) {
                close(response.body());
                throw new IllegalArgumentException("The web page exceeds the maximum response size");
            }
            return new FetchedWebPage(current, readBounded(response.body()), contentType);
        }
        throw new IllegalStateException("Redirect processing did not terminate");
    }

    private HttpResponse<InputStream> send(URI uri) {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(requestTimeout)
                .header("Accept", "text/html,application/xhtml+xml")
                .header("User-Agent", "RAG-Lab-Web-Importer/1.0")
                .GET()
                .build();
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalArgumentException("The web page request was interrupted", exception);
        } catch (IOException exception) {
            throw new IllegalArgumentException("The web page could not be downloaded", exception);
        }
    }

    private byte[] readBounded(InputStream input) {
        try (input) {
            byte[] bytes = input.readNBytes(maximumBytes + 1);
            if (bytes.length > maximumBytes) {
                throw new IllegalArgumentException("The web page exceeds the maximum response size");
            }
            return bytes;
        } catch (IOException exception) {
            throw new IllegalArgumentException("The web page response could not be read", exception);
        }
    }

    private URI normalizeAndValidate(URI uri) {
        if (uri == null || uri.getScheme() == null || uri.getHost() == null) {
            throw new IllegalArgumentException("A valid absolute web page URL is required");
        }
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        if (!("http".equals(scheme) || "https".equals(scheme)) || uri.getUserInfo() != null) {
            throw new IllegalArgumentException("Only public HTTP and HTTPS URLs are allowed");
        }
        try {
            for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
                if (!isPublic(address)) {
                    throw new IllegalArgumentException("Private and local network URLs are not allowed");
                }
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("The web page host could not be resolved", exception);
        }
        return uri.normalize();
    }

    private boolean isPublic(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return false;
        }
        if (address instanceof Inet6Address) {
            byte first = address.getAddress()[0];
            return (first & 0xfe) != 0xfc;
        }
        byte[] bytes = address.getAddress();
        int first = Byte.toUnsignedInt(bytes[0]);
        int second = Byte.toUnsignedInt(bytes[1]);
        return !(first == 0 || first == 10 || first == 127
                || (first == 169 && second == 254)
                || (first == 172 && second >= 16 && second <= 31)
                || (first == 192 && second == 168)
                || first >= 224);
    }

    private void close(InputStream input) {
        try {
            input.close();
        } catch (IOException ignored) {
            // The response is already unusable; preserve the original validation error.
        }
    }
}
