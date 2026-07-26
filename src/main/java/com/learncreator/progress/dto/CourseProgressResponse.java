package com.learncreator.progress.dto;

import com.learncreator.enrollments.entity.EnrollmentStatus;

import java.util.List;
import java.util.UUID;

public record CourseProgressResponse(
        UUID courseId,
        int totalLessons,
        int completedLessons,
        int percentComplete,
        EnrollmentStatus enrollmentStatus,
        List<LessonProgressResponse> lessons
) {}
