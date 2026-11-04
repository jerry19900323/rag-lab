package com.example.raglab.infrastructure.document;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/** Extracts formatted cell values while retaining worksheet names and row boundaries. */
@Component
public class XlsxDocumentParser implements DocumentFormatParser {

    @Override
    public boolean supports(String extension) {
        return "xlsx".equals(extension);
    }

    @Override
    public ParsedDocument parse(MultipartFile file, String fileName) {
        try (var workbook = new XSSFWorkbook(file.getInputStream())) {
            StringBuilder content = new StringBuilder();
            List<SourceSegment> segments = new ArrayList<>();
            DataFormatter formatter = new DataFormatter();
            var evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            for (var sheet : workbook) {
                for (var row : sheet) {
                    List<String> values = new ArrayList<>();
                    for (int cellIndex = row.getFirstCellNum();
                            cellIndex >= 0 && cellIndex < row.getLastCellNum(); cellIndex++) {
                        var cell = row.getCell(cellIndex);
                        values.add(cell == null ? "" : formatter.formatCellValue(cell, evaluator).strip());
                    }
                    while (!values.isEmpty() && values.get(values.size() - 1).isEmpty()) {
                        values.remove(values.size() - 1);
                    }
                    String rowText = String.join("\t", values).strip();
                    if (rowText.isEmpty()) {
                        continue;
                    }
                    if (!content.isEmpty()) {
                        content.append('\n');
                    }
                    int start = content.length();
                    content.append(rowText);
                    segments.add(new SourceSegment(
                            start, content.length(), null, null, sheet.getSheetName(),
                            null, "ROW"));
                }
            }
            if (content.isEmpty()) {
                throw new IllegalArgumentException("The Excel document does not contain readable cells");
            }
            return new ParsedDocument(fileName, "EXCEL", content.toString(), segments);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new IllegalArgumentException("The Excel document could not be read", exception);
        }
    }
}
