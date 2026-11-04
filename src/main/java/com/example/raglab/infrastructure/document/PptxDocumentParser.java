package com.example.raglab.infrastructure.document;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFTable;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/** Extracts visible text and slide numbers from modern PowerPoint files. */
@Component
public class PptxDocumentParser implements DocumentFormatParser {

    @Override
    public boolean supports(String extension) {
        return "pptx".equals(extension);
    }

    @Override
    public ParsedDocument parse(MultipartFile file, String fileName) {
        try (var presentation = new XMLSlideShow(file.getInputStream())) {
            StringBuilder content = new StringBuilder();
            List<SourceSegment> segments = new ArrayList<>();
            for (int index = 0; index < presentation.getSlides().size(); index++) {
                int slideNumber = index + 1;
                StringBuilder slideText = new StringBuilder();
                for (var shape : presentation.getSlides().get(index).getShapes()) {
                    String text = "";
                    if (shape instanceof XSLFTextShape textShape) {
                        text = textShape.getText();
                    } else if (shape instanceof XSLFTable table) {
                        text = table.getRows().stream()
                                .map(row -> row.getCells().stream()
                                        .map(cell -> cell.getText().strip())
                                        .reduce((left, right) -> left + "\t" + right).orElse(""))
                                .reduce((left, right) -> left + "\n" + right).orElse("");
                    }
                    text = DocumentParserRouter.normalizeText(text).strip();
                    if (!text.isEmpty()) {
                        if (!slideText.isEmpty()) {
                            slideText.append('\n');
                        }
                        slideText.append(text);
                    }
                }
                if (slideText.isEmpty()) {
                    continue;
                }
                if (!content.isEmpty()) {
                    content.append("\n\n");
                }
                int start = content.length();
                content.append(slideText);
                segments.add(new SourceSegment(
                        start, content.length(), null, slideNumber, null, null, "SLIDE"));
            }
            if (content.isEmpty()) {
                throw new IllegalArgumentException("The PowerPoint document does not contain readable text");
            }
            return new ParsedDocument(fileName, "POWERPOINT", content.toString(), segments);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new IllegalArgumentException("The PowerPoint document could not be read", exception);
        }
    }
}
