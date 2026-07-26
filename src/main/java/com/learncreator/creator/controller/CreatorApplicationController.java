package com.learncreator.creator.controller;

import com.learncreator.auth.entity.User;
import com.learncreator.creator.dto.CreatorApplicationRequest;
import com.learncreator.creator.dto.CreatorApplicationResponse;
import com.learncreator.creator.service.CreatorApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/creator")
@RequiredArgsConstructor
public class CreatorApplicationController {

    private final CreatorApplicationService applicationService;

    @PostMapping("/apply")
    public ResponseEntity<CreatorApplicationResponse> apply(
            @Valid @RequestBody CreatorApplicationRequest request,
            Authentication authentication
    ) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.status(HttpStatus.CREATED).body(applicationService.apply(request, user));
    }

    @GetMapping("/applications/mine")
    public ResponseEntity<List<CreatorApplicationResponse>> myApplications(Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(applicationService.myApplications(user));
    }
}
