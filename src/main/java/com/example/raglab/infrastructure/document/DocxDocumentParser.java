package com.example.raglab.infrastructure.document;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/** Extracts paragraphs, headings, and tables from modern Word documents. */
@Component
public class DocxDocumentParser implements DocumentFormatParser {

    @Override
    public boolean supports(String extension) {
        return "docx".equals(extension);
    }

    @Override
    public ParsedDocument parse(MultipartFile file, String fileName) {
        try (var document = new XWPFDocument(file.getInputStream())) {
            StringBuilder content = new StringBuilder();
            List<SourceSegment> segments = new ArrayList<>();
            String currentHeading = null;
            for (IBodyElement element : document.getBodyElements()) {
                String text;
                String type;
                if (element instanceof XWPFParagraph paragraph) {
                    text = DocumentParserRouter.normalizeText(paragraph.getText()).strip();
                    type = isHeading(paragraph) ? "HEADING" : "PARAGRAPH";
                    if ("HEADING".equals(type) && !text.isEmpty()) {
                        currentHeading = text;
                    }
                } else if (element instanceof XWPFTable table) {
                    text = table.getRows().stream()
                            .map(row -> row.getTableCells().stream()
                                    .map(cell -> cell.getText().strip())
                                    .reduce((left, right) -> left + "\t" + right).orElse(""))
                            .reduce((left, right) -> left + "\n" + right).orElse("").strip();
                    type = "TABLE";
                } else {
                    continue;
                }
                if (text.isEmpty()) {
                    continue;
                }
                if (!content.isEmpty()) {
                    content.append("\n\n");
                }
                int start = content.length();
                content.append(text);
                segments.add(new SourceSegment(
                        start, content.length(), null, null, null, currentHeading, type));
            }
            if (content.isEmpty()) {
                throw new IllegalArgumentException("The Word document does not contain readable text");
            }
            return new ParsedDocument(fileName, "WORD", content.toString(), segments);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new IllegalArgumentException("The Word document could not be read", exception);
        }
    }

    private boolean isHeading(XWPFParagraph paragraph) {
        String style = paragraph.getStyle();
        return style != null && style.toLowerCase(java.util.Locale.ROOT).startsWith("heading");
    }
}
