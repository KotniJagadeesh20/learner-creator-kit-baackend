package com.learncreator.courses.controller;

import com.learncreator.auth.entity.User;
import com.learncreator.courses.dto.*;
import com.learncreator.courses.entity.CourseStatus;
import com.learncreator.courses.service.CourseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;

    // ---- Public browsing (no auth) ----

    @GetMapping
    public ResponseEntity<List<CourseResponse>> listPublished() {
        return ResponseEntity.ok(courseService.listPublished());
    }

    @GetMapping("/{courseId}")
    public ResponseEntity<CourseResponse> getById(
            @PathVariable UUID courseId,
            Authentication authentication
    ) {
        User requester = currentUserOrNull(authentication);
        return ResponseEntity.ok(courseService.getById(courseId, requester));
    }

    // ---- Creator-authenticated ----

    @GetMapping("/mine")
    public ResponseEntity<List<CourseResponse>> listMine(Authentication authentication) {
        return ResponseEntity.ok(courseService.listMine(currentUser(authentication)));
    }

    @PostMapping
    public ResponseEntity<CourseResponse> create(
            @Valid @RequestBody CourseRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(courseService.create(request, currentUser(authentication)));
    }

    @PutMapping("/{courseId}")
    public ResponseEntity<CourseResponse> update(
            @PathVariable UUID courseId,
            @Valid @RequestBody CourseRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(courseService.update(courseId, request, currentUser(authentication)));
    }

    @PatchMapping("/{courseId}/status")
    public ResponseEntity<CourseResponse> setStatus(
            @PathVariable UUID courseId,
            @RequestParam CourseStatus status,
            Authentication authentication
    ) {
        return ResponseEntity.ok(courseService.setStatus(courseId, status, currentUser(authentication)));
    }

    @DeleteMapping("/{courseId}")
    public ResponseEntity<Void> delete(@PathVariable UUID courseId, Authentication authentication) {
        courseService.delete(courseId, currentUser(authentication));
        return ResponseEntity.noContent().build();
    }

    // ---- Modules ----

    @PostMapping("/{courseId}/modules")
    public ResponseEntity<ModuleResponse> addModule(
            @PathVariable UUID courseId,
            @Valid @RequestBody ModuleRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(courseService.addModule(courseId, request, currentUser(authentication)));
    }

    // ---- Lessons ----

    @PostMapping("/modules/{moduleId}/lessons")
    public ResponseEntity<LessonResponse> addLesson(
            @PathVariable UUID moduleId,
            @Valid @RequestBody LessonRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(courseService.addLesson(moduleId, request, currentUser(authentication)));
    }

    // ---- Helpers ----
    // JwtAuthFilter sets the actual User entity as the Authentication principal (see auth module),
    // so no extra DB lookup is needed here to identify the caller.

    private User currentUser(Authentication authentication) {
        return (User) authentication.getPrincipal();
    }

    private User currentUserOrNull(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return null;
        }
        return (User) authentication.getPrincipal();
    }
}
