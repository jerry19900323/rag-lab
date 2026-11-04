package com.example.raglab.infrastructure.document;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/** Extracts text and page locations from PDFs that contain a text layer. */
@Component
public class PdfDocumentParser implements DocumentFormatParser {

    @Override
    public boolean supports(String extension) {
        return "pdf".equals(extension);
    }

    @Override
    public ParsedDocument parse(MultipartFile file, String fileName) {
        try (var document = Loader.loadPDF(file.getBytes())) {
            if (document.isEncrypted()) {
                throw new IllegalArgumentException("Encrypted PDF documents are not supported");
            }
            StringBuilder content = new StringBuilder();
            List<SourceSegment> segments = new ArrayList<>();
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String pageText = DocumentParserRouter.normalizeText(stripper.getText(document)).strip();
                if (pageText.isEmpty()) {
                    continue;
                }
                if (!content.isEmpty()) {
                    content.append("\n\n");
                }
                int start = content.length();
                content.append(pageText);
                segments.add(new SourceSegment(
                        start, content.length(), page, null, null, null, "PAGE"));
            }
            if (content.isEmpty()) {
                throw new IllegalArgumentException(
                        "The PDF does not contain readable text; scanned PDFs require OCR");
            }
            return new ParsedDocument(fileName, "PDF", content.toString(), segments);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (InvalidPasswordException exception) {
            throw new IllegalArgumentException("Encrypted PDF documents are not supported", exception);
        } catch (IOException exception) {
            throw new IllegalArgumentException("The PDF document could not be read", exception);
        }
    }
}
