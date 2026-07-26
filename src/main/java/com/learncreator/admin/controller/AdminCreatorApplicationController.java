package com.learncreator.admin.controller;

import com.learncreator.auth.entity.User;
import com.learncreator.creator.dto.CreatorApplicationResponse;
import com.learncreator.creator.dto.RejectApplicationRequest;
import com.learncreator.creator.service.CreatorApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Deliberately thin for v1 — this is the admin module's only surface area right now
 * (creator-application review). Moderation/takedown/analytics endpoints get added here later.
 */
@RestController
@RequestMapping("/api/admin/creator-applications")
@RequiredArgsConstructor
public class AdminCreatorApplicationController {

    private final CreatorApplicationService applicationService;

    @GetMapping
    public ResponseEntity<List<CreatorApplicationResponse>> listPending() {
        return ResponseEntity.ok(applicationService.listPending());
    }

    @PostMapping("/{applicationId}/approve")
    public ResponseEntity<CreatorApplicationResponse> approve(
            @PathVariable UUID applicationId,
            Authentication authentication
    ) {
        User admin = (User) authentication.getPrincipal();
        return ResponseEntity.ok(applicationService.approve(applicationId, admin));
    }

    @PostMapping("/{applicationId}/reject")
    public ResponseEntity<CreatorApplicationResponse> reject(
            @PathVariable UUID applicationId,
            @RequestBody(required = false) RejectApplicationRequest request,
            Authentication authentication
    ) {
        User admin = (User) authentication.getPrincipal();
        String reason = request != null ? request.reason() : null;
        return ResponseEntity.ok(applicationService.reject(applicationId, reason, admin));
    }
}
