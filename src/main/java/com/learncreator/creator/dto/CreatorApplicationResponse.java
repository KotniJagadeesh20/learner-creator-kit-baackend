package com.learncreator.creator.dto;

import com.learncreator.creator.entity.ApplicationStatus;
import com.learncreator.creator.entity.CreatorApplication;

import java.time.Instant;
import java.util.UUID;

public record CreatorApplicationResponse(
        UUID id,
        UUID userId,
        String userName,
        String userEmail,
        String pitch,
        ApplicationStatus status,
        String rejectionReason,
        Instant createdAt,
        Instant reviewedAt
) {
    public static CreatorApplicationResponse from(CreatorApplication app) {
        return new CreatorApplicationResponse(
                app.getId(),
                app.getUser().getId(),
                app.getUser().getName(),
                app.getUser().getEmail(),
                app.getPitch(),
                app.getStatus(),
                app.getRejectionReason(),
                app.getCreatedAt(),
                app.getReviewedAt()
        );
    }
}
