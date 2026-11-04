package com.example.raglab.infrastructure.document;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.ParserDelegator;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/** Extracts readable HTML blocks while discarding navigation and executable markup. */
@Component
public class HtmlDocumentParser implements DocumentFormatParser {

    @Override
    public boolean supports(String extension) {
        return "html".equals(extension) || "htm".equals(extension);
    }

    @Override
    public ParsedDocument parse(MultipartFile file, String fileName) {
        try (var reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8)) {
            HtmlTextCallback callback = new HtmlTextCallback();
            new ParserDelegator().parse(reader, callback, true);
            callback.finish();
            if (callback.content.isEmpty()) {
                throw new IllegalArgumentException("The HTML document does not contain readable text");
            }
            return new ParsedDocument(fileName, "HTML", callback.content.toString(), callback.segments);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("The HTML document could not be read", exception);
        }
    }

    private static final class HtmlTextCallback extends HTMLEditorKit.ParserCallback {

        private final StringBuilder content = new StringBuilder();
        private final List<SourceSegment> segments = new ArrayList<>();
        private final StringBuilder block = new StringBuilder();
        private int excludedDepth;
        private HTML.Tag blockTag;
        private String currentHeading;

        @Override
        public void handleStartTag(HTML.Tag tag, MutableAttributeSet attributes, int position) {
            if (isExcluded(tag)) {
                excludedDepth++;
                return;
            }
            if (excludedDepth > 0) {
                return;
            }
            if (isBlock(tag)) {
                flushBlock();
                blockTag = tag;
            } else if ((tag == HTML.Tag.TD || tag == HTML.Tag.TH) && blockTag == HTML.Tag.TR
                    && !block.isEmpty()) {
                block.append('\t');
            }
        }

        @Override
        public void handleEndTag(HTML.Tag tag, int position) {
            if (isExcluded(tag)) {
                excludedDepth = Math.max(0, excludedDepth - 1);
                return;
            }
            if (excludedDepth == 0 && tag == blockTag) {
                flushBlock();
            }
        }

        @Override
        public void handleText(char[] data, int position) {
            if (excludedDepth == 0 && blockTag != null) {
                if (!block.isEmpty() && !Character.isWhitespace(block.charAt(block.length() - 1))) {
                    block.append(' ');
                }
                block.append(data);
            }
        }

        private void finish() {
            flushBlock();
        }

        private void flushBlock() {
            String text = block.toString().strip();
            text = blockTag == HTML.Tag.TR
                    ? text.replaceAll(" *\\t *", "\t")
                    : text.replaceAll("[ \\t]+", " ");
            if (text.isEmpty() || blockTag == null) {
                block.setLength(0);
                return;
            }
            String type = isHeading(blockTag) ? "HEADING"
                    : blockTag == HTML.Tag.TR ? "TABLE_ROW" : "HTML_BLOCK";
            if ("HEADING".equals(type)) {
                currentHeading = text;
            }
            if (!content.isEmpty()) {
                content.append("\n\n");
            }
            int start = content.length();
            content.append(text);
            segments.add(new SourceSegment(
                    start, content.length(), null, null, null, currentHeading, type));
            block.setLength(0);
            blockTag = null;
        }

        private boolean isBlock(HTML.Tag tag) {
            return isHeading(tag) || tag == HTML.Tag.P || tag == HTML.Tag.LI
                    || tag == HTML.Tag.PRE || tag == HTML.Tag.BLOCKQUOTE || tag == HTML.Tag.TR;
        }

        private boolean isHeading(HTML.Tag tag) {
            return tag == HTML.Tag.H1 || tag == HTML.Tag.H2 || tag == HTML.Tag.H3
                    || tag == HTML.Tag.H4 || tag == HTML.Tag.H5 || tag == HTML.Tag.H6;
        }

        private boolean isExcluded(HTML.Tag tag) {
            return tag == HTML.Tag.SCRIPT || tag == HTML.Tag.STYLE || tag == HTML.Tag.HEAD
                    || "nav".equals(tag.toString()) || "header".equals(tag.toString())
                    || "footer".equals(tag.toString()) || "aside".equals(tag.toString())
                    || "noscript".equals(tag.toString());
        }
    }
}
