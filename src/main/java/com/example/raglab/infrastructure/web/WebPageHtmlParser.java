package com.example.raglab.infrastructure.web;

import com.example.raglab.infrastructure.document.ParsedDocument;
import com.example.raglab.infrastructure.document.SourceSegment;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

/** Extracts readable blocks, traceable page metadata, and static next-page links. */
@Component
public class WebPageHtmlParser {

    private static final String BLOCK_SELECTOR = "h1,h2,h3,h4,h5,h6,p,li,pre,blockquote,tr";
    private static final Set<String> BLOCK_TAGS = Set.of(
            "h1", "h2", "h3", "h4", "h5", "h6", "p", "li", "pre", "blockquote", "tr");
    private static final Set<String> NEXT_TEXT = Set.of(
            "next", "next page", "下一页", "下页", "后页", ">", "›", "»");

    public ParsedWebPage parse(FetchedWebPage fetched, int pageNumber) {
        try {
            Document html = Jsoup.parse(
                    new ByteArrayInputStream(fetched.body()), null, fetched.uri().toString());
            String title = html.title().isBlank() ? fetched.uri().getHost() : html.title().strip();
            URI nextPage = findNextPage(html, fetched.uri());
            html.select("script,style,noscript,nav,header,footer,aside,form,svg,canvas").remove();

            StringBuilder content = new StringBuilder();
            List<SourceSegment> segments = new ArrayList<>();
            String currentHeading = null;
            for (Element element : html.select(BLOCK_SELECTOR)) {
                if (hasSelectedBlockParent(element)) {
                    continue;
                }
                String text = normalize(element.text());
                if (text.isEmpty()) {
                    continue;
                }
                String tag = element.normalName();
                String type = tag.matches("h[1-6]") ? "HEADING"
                        : "tr".equals(tag) ? "TABLE_ROW" : "HTML_BLOCK";
                if ("HEADING".equals(type)) {
                    currentHeading = text;
                }
                if (!content.isEmpty()) {
                    content.append("\n\n");
                }
                int start = content.length();
                content.append(text);
                segments.add(new SourceSegment(
                        start, content.length(), null, null, null, currentHeading, type,
                        fetched.uri().toString(), title, pageNumber));
            }
            if (content.isEmpty()) {
                String fallback = normalize(html.body() == null ? "" : html.body().text());
                if (fallback.isEmpty()) {
                    throw new IllegalArgumentException("The web page does not contain readable text");
                }
                content.append(fallback);
                segments.add(new SourceSegment(
                        0, fallback.length(), null, null, null, null, "HTML_BLOCK",
                        fetched.uri().toString(), title, pageNumber));
            }
            String fileName = fetched.uri().getHost().replaceAll("[^A-Za-z0-9.-]", "_") + ".html";
            return new ParsedWebPage(
                    fetched.uri(), title,
                    new ParsedDocument(fileName, "HTML", content.toString(), segments), nextPage);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("The web page HTML could not be parsed", exception);
        }
    }

    private URI findNextPage(Document html, URI current) {
        Element explicit = html.selectFirst("link[rel~=next],a[rel~=next]");
        if (explicit != null) {
            URI resolved = resolve(explicit, current);
            if (resolved != null) {
                return resolved;
            }
        }
        for (Element link : html.select("a[href]")) {
            String text = normalize(link.text()).toLowerCase(Locale.ROOT);
            if (NEXT_TEXT.contains(text)) {
                return resolve(link, current);
            }
        }
        return null;
    }

    private URI resolve(Element link, URI current) {
        String href = link.attr("href").strip();
        if (href.isEmpty() || href.startsWith("#")) {
            return null;
        }
        try {
            return current.resolve(href).normalize();
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private boolean hasSelectedBlockParent(Element element) {
        return element.parents().stream().anyMatch(parent -> BLOCK_TAGS.contains(parent.normalName()));
    }

    private String normalize(String text) {
        return text == null ? "" : text.replaceAll("\\s+", " ").strip();
    }
}
