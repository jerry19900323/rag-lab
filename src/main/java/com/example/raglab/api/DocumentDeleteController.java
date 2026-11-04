package com.example.raglab.api;

import com.example.raglab.application.AdminRequiredException;
import com.example.raglab.application.AuthUser;
import com.example.raglab.application.DocumentDeletionService;
import com.example.raglab.infrastructure.auth.BearerAuthenticationFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Administrator deletion of indexed documents. */
@RestController
@RequestMapping("/api/documents")
public class DocumentDeleteController {

    private final DocumentDeletionService service;

    public DocumentDeleteController(DocumentDeletionService service) {
        this.service = service;
    }

    @DeleteMapping("/{documentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(HttpServletRequest request, @PathVariable String documentId) {
        AuthUser user = (AuthUser) request.getAttribute(BearerAuthenticationFilter.USER_ATTRIBUTE);
        if (user == null || !user.isAdmin()) {
            throw new AdminRequiredException();
        }
        service.delete(documentId);
    }
}
