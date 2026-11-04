package com.example.raglab.api;

import com.example.raglab.application.WebPageImportRequest;
import com.example.raglab.application.WebPageImportService;
import com.example.raglab.application.WebPageIndexingResult;
import com.example.raglab.application.WebPagePreviewResult;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Exposes safe public web-page preview and indexing operations. */
@RestController
@RequestMapping("/api/web-pages")
public class WebPageImportController {

    private final WebPageImportService service;

    public WebPageImportController(WebPageImportService service) {
        this.service = service;
    }

    @PostMapping("/preview")
    public WebPagePreviewResult preview(@Valid @RequestBody WebPageImportRequest request) {
        return service.preview(request);
    }

    @PostMapping("/index")
    @ResponseStatus(HttpStatus.CREATED)
    public WebPageIndexingResult index(@Valid @RequestBody WebPageImportRequest request) {
        return service.index(request);
    }
}
