package com.learncreator.enrollments.dto;

import com.learncreator.enrollments.entity.Enrollment;
import com.learncreator.enrollments.entity.EnrollmentStatus;

import java.time.Instant;
import java.util.UUID;

public record EnrollmentResponse(
        UUID id,
        UUID courseId,
        String courseTitle,
        String courseThumbnailUrl,
        EnrollmentStatus status,
        Instant enrolledAt,
        Instant completedAt
) {
    public static EnrollmentResponse from(Enrollment enrollment) {
        return new EnrollmentResponse(
                enrollment.getId(),
                enrollment.getCourse().getId(),
                enrollment.getCourse().getTitle(),
                enrollment.getCourse().getThumbnailUrl(),
                enrollment.getStatus(),
                enrollment.getEnrolledAt(),
                enrollment.getCompletedAt()
        );
    }
}
