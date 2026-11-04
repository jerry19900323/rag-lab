package com.example.raglab.api;

import com.example.raglab.application.AdminRequiredException;
import com.example.raglab.application.AuthUser;
import com.example.raglab.application.ModelAssignment;
import com.example.raglab.application.ModelAssignmentInput;
import com.example.raglab.application.ModelConnectionInput;
import com.example.raglab.application.ModelConnectionView;
import com.example.raglab.application.ModelOption;
import com.example.raglab.application.ModelPurpose;
import com.example.raglab.application.ModelSettingsService;
import com.example.raglab.application.ModelUsageTypeView;
import com.example.raglab.infrastructure.auth.BearerAuthenticationFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Administrator API for model configuration and usage assignments. */
@RestController
@RequestMapping("/api/admin")
public class ModelSettingsController {

    private final ModelSettingsService service;

    public ModelSettingsController(ModelSettingsService service) {
        this.service = service;
    }

    @GetMapping("/model-providers")
    public List<ModelSettingsService.ModelProviderView> providers(HttpServletRequest request) {
        requireAdmin(request);
        return service.providers();
    }

    @GetMapping("/model-usage-types")
    public List<ModelUsageTypeView> usageTypes(HttpServletRequest request) {
        requireAdmin(request);
        return service.usageTypes();
    }

    @GetMapping("/model-connections")
    public List<ModelConnectionView> connections(HttpServletRequest request) {
        requireAdmin(request);
        return service.connections();
    }

    @PostMapping("/model-connections")
    @ResponseStatus(HttpStatus.CREATED)
    public ModelConnectionView create(HttpServletRequest request, @RequestBody ModelConnectionInput input) {
        requireAdmin(request);
        return service.create(input);
    }

    @PutMapping("/model-connections/{id}")
    public ModelConnectionView update(HttpServletRequest request, @PathVariable String id,
            @RequestBody ModelConnectionInput input) {
        requireAdmin(request);
        return service.update(id, input);
    }

    @DeleteMapping("/model-connections/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(HttpServletRequest request, @PathVariable String id) {
        requireAdmin(request);
        service.delete(id);
    }

    @GetMapping("/model-connections/{id}/models")
    public List<ModelOption> models(HttpServletRequest request, @PathVariable String id) {
        requireAdmin(request);
        return service.models(id);
    }

    @PostMapping("/model-connections/{id}/test")
    public ModelSettingsService.ModelConnectionTestResult test(HttpServletRequest request, @PathVariable String id) {
        requireAdmin(request);
        return service.test(id);
    }

    @GetMapping("/model-assignments")
    public List<ModelAssignment> assignments(HttpServletRequest request) {
        requireAdmin(request);
        return service.assignments();
    }

    @PutMapping("/model-assignments/{purpose}")
    public ModelAssignment assign(HttpServletRequest request, @PathVariable ModelPurpose purpose,
            @RequestBody ModelAssignmentInput input) {
        requireAdmin(request);
        return service.assign(purpose, input);
    }

    private void requireAdmin(HttpServletRequest request) {
        AuthUser user = (AuthUser) request.getAttribute(BearerAuthenticationFilter.USER_ATTRIBUTE);
        if (user == null || !user.isAdmin()) {
            throw new AdminRequiredException();
        }
    }
}
