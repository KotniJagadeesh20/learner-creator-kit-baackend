package com.learncreator.progress.controller;

import com.learncreator.auth.entity.User;
import com.learncreator.progress.dto.CourseProgressResponse;
import com.learncreator.progress.service.ProgressService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/progress")
@RequiredArgsConstructor
public class ProgressController {

    private final ProgressService progressService;

    @PostMapping("/lessons/{lessonId}/complete")
    public ResponseEntity<CourseProgressResponse> markLessonComplete(
            @PathVariable UUID lessonId,
            Authentication authentication
    ) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(progressService.markLessonComplete(lessonId, user));
    }

    @GetMapping("/courses/{courseId}")
    public ResponseEntity<CourseProgressResponse> getCourseProgress(
            @PathVariable UUID courseId,
            Authentication authentication
    ) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(progressService.getCourseProgress(courseId, user));
    }
}
