package com.example.raglab.application;

import com.example.raglab.infrastructure.document.ParsedDocument;
import com.example.raglab.infrastructure.document.SourceSegment;
import com.example.raglab.infrastructure.web.ParsedWebPage;
import com.example.raglab.infrastructure.web.WebPageClient;
import com.example.raglab.infrastructure.web.WebPageHtmlParser;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;

/** Crawls bounded static pagination and reuses the document processing and indexing pipeline. */
@Service
public class WebPageImportService {

    private static final int MAXIMUM_PAGE_LIMIT = 50;

    private final WebPageClient pageClient;
    private final WebPageHtmlParser htmlParser;
    private final DocumentProcessingService processingService;
    private final DocumentIndexingService indexingService;
    private final Sha256HashService hashService;

    public WebPageImportService(
            WebPageClient pageClient,
            WebPageHtmlParser htmlParser,
            DocumentProcessingService processingService,
            DocumentIndexingService indexingService,
            Sha256HashService hashService) {
        this.pageClient = pageClient;
        this.htmlParser = htmlParser;
        this.processingService = processingService;
        this.indexingService = indexingService;
        this.hashService = hashService;
    }

    public WebPagePreviewResult preview(WebPageImportRequest request) {
        CrawlResult crawl = crawl(request);
        DocumentProcessingResult document = processingService.preview(
                crawl.document(), request.maxCharacters(), request.overlapCharacters());
        return new WebPagePreviewResult(
                request.url(), request.followPagination(), crawl.pages(), document);
    }

    public WebPageIndexingResult index(WebPageImportRequest request) {
        CrawlResult crawl = crawl(request);
        DocumentProcessingResult processed = processingService.preview(
                crawl.document(), request.maxCharacters(), request.overlapCharacters());
        DocumentIndexingResult indexed = indexingService.indexProcessed(
                processed, request.documentId(), hashService.hashText(crawl.document().content()));
        return new WebPageIndexingResult(
                request.url(), request.followPagination(), crawl.pages(), indexed);
    }

    private CrawlResult crawl(WebPageImportRequest request) {
        if (request.maxPages() < 1 || request.maxPages() > MAXIMUM_PAGE_LIMIT) {
            throw new IllegalArgumentException("maxPages must be between 1 and 50");
        }
        URI initial = parseUri(request.url());
        URI current = withoutFragment(initial);
        URI paginationOrigin = null;
        Set<URI> visited = new LinkedHashSet<>();
        List<ParsedWebPage> parsedPages = new ArrayList<>();

        while (current != null && parsedPages.size() < request.maxPages() && visited.add(current)) {
            ParsedWebPage parsed = htmlParser.parse(pageClient.fetch(current), parsedPages.size() + 1);
            parsedPages.add(parsed);
            if (paginationOrigin == null) {
                paginationOrigin = parsed.uri();
            }
            if (!request.followPagination() || parsed.nextPage() == null) {
                break;
            }
            URI next = withoutFragment(parsed.nextPage());
            current = sameOrigin(paginationOrigin, next) ? next : null;
        }
        if (parsedPages.isEmpty()) {
            throw new IllegalArgumentException("No readable web pages were found");
        }
        return combine(parsedPages);
    }

    private CrawlResult combine(List<ParsedWebPage> pages) {
        StringBuilder content = new StringBuilder();
        List<SourceSegment> segments = new ArrayList<>();
        List<WebPageInfo> pageInfos = new ArrayList<>();

        for (int index = 0; index < pages.size(); index++) {
            ParsedWebPage page = pages.get(index);
            if (!content.isEmpty()) {
                content.append("\n\n");
            }
            int offset = content.length();
            content.append(page.document().content());
            for (SourceSegment segment : page.document().sourceSegments()) {
                segments.add(new SourceSegment(
                        segment.startOffset() + offset,
                        segment.endOffset() + offset,
                        segment.pageNumber(), segment.slideNumber(), segment.sheetName(),
                        segment.sectionTitle(), segment.elementType(), segment.sourceUrl(),
                        segment.sourceTitle(), segment.webPageNumber()));
            }
            pageInfos.add(new WebPageInfo(index + 1, page.uri().toString(), page.title()));
        }
        ParsedWebPage first = pages.get(0);
        ParsedDocument combined = new ParsedDocument(
                first.document().fileName(), "HTML", content.toString(), segments);
        return new CrawlResult(combined, pageInfos);
    }

    private URI parseUri(String value) {
        try {
            return new URI(value.strip());
        } catch (RuntimeException | URISyntaxException exception) {
            throw new IllegalArgumentException("A valid absolute web page URL is required", exception);
        }
    }

    private URI withoutFragment(URI uri) {
        try {
            return new URI(uri.getScheme(), uri.getUserInfo(), uri.getHost(), uri.getPort(),
                    uri.getPath(), uri.getQuery(), null).normalize();
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("A valid web page URL is required", exception);
        }
    }

    private boolean sameOrigin(URI first, URI second) {
        if (first.getScheme() == null || first.getHost() == null
                || second.getScheme() == null || second.getHost() == null) {
            return false;
        }
        return first.getScheme().equalsIgnoreCase(second.getScheme())
                && first.getHost().equalsIgnoreCase(second.getHost())
                && effectivePort(first) == effectivePort(second);
    }

    private int effectivePort(URI uri) {
        if (uri.getPort() >= 0) {
            return uri.getPort();
        }
        return "https".equals(uri.getScheme().toLowerCase(Locale.ROOT)) ? 443 : 80;
    }

    private record CrawlResult(ParsedDocument document, List<WebPageInfo> pages) {
    }
}
