package com.learncreator.courses.dto;

import com.learncreator.courses.entity.CourseModule;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public record ModuleResponse(
        UUID id,
        String title,
        int orderIndex,
        List<LessonResponse> lessons
) {
    public static ModuleResponse from(CourseModule module) {
        return new ModuleResponse(
                module.getId(),
                module.getTitle(),
                module.getOrderIndex(),
                module.getLessons().stream().map(LessonResponse::from).collect(Collectors.toList())
        );
    }
}
