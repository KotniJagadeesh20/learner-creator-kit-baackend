package com.learncreator.enrollments.controller;

import com.learncreator.auth.entity.User;
import com.learncreator.enrollments.dto.EnrollRequest;
import com.learncreator.enrollments.dto.EnrollmentResponse;
import com.learncreator.enrollments.service.EnrollmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/enrollments")
@RequiredArgsConstructor
public class EnrollmentController {

    private final EnrollmentService enrollmentService;

    @PostMapping
    public ResponseEntity<EnrollmentResponse> enroll(
            @Valid @RequestBody EnrollRequest request,
            Authentication authentication
    ) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.status(HttpStatus.CREATED).body(enrollmentService.enroll(request.courseId(), user));
    }

    @GetMapping("/mine")
    public ResponseEntity<List<EnrollmentResponse>> listMine(Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(enrollmentService.listMine(user));
    }

    @GetMapping("/course/{courseId}")
    public ResponseEntity<EnrollmentResponse> getForCourse(
            @PathVariable UUID courseId,
            Authentication authentication
    ) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(enrollmentService.getForCourse(courseId, user));
    }
}
