package com.learncreator.courses.dto;

import com.learncreator.courses.entity.Course;
import com.learncreator.courses.entity.CourseLevel;
import com.learncreator.courses.entity.CourseStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public record CourseResponse(
        UUID id,
        String title,
        String description,
        String thumbnailUrl,
        String category,
        CourseLevel level,
        CourseStatus status,
        Double price,
        UUID creatorId,
        String creatorName,
        List<ModuleResponse> modules,
        Instant createdAt
) {
    public static CourseResponse from(Course course) {
        return new CourseResponse(
                course.getId(),
                course.getTitle(),
                course.getDescription(),
                course.getThumbnailUrl(),
                course.getCategory(),
                course.getLevel(),
                course.getStatus(),
                course.getPrice(),
                course.getCreator().getId(),
                course.getCreator().getName(),
                course.getModules().stream().map(ModuleResponse::from).collect(Collectors.toList()),
                course.getCreatedAt()
        );
    }

    // Lightweight variant for list views — skips nested modules/lessons to keep list payloads small.
    public static CourseResponse summary(Course course) {
        return new CourseResponse(
                course.getId(),
                course.getTitle(),
                course.getDescription(),
                course.getThumbnailUrl(),
                course.getCategory(),
                course.getLevel(),
                course.getStatus(),
                course.getPrice(),
                course.getCreator().getId(),
                course.getCreator().getName(),
                List.of(),
                course.getCreatedAt()
        );
    }

    public static CourseResponse publicDetails(Course course) {
        CourseResponse response = from(course);
        return new CourseResponse(
                response.id(), response.title(), response.description(), response.thumbnailUrl(),
                response.category(), response.level(), response.status(), response.price(),
                response.creatorId(), response.creatorName(),
                course.getModules().stream().map(ModuleResponse::publicMetadata).collect(Collectors.toList()),
                response.createdAt()
        );
    }
}
