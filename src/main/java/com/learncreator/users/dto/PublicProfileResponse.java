package com.learncreator.users.dto;

import com.learncreator.auth.entity.Role;
import com.learncreator.auth.entity.User;
import com.learncreator.courses.entity.Course;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public record PublicProfileResponse(
        UUID id,
        String name,
        String bio,
        String avatarUrl,
        Role role,
        Instant createdAt,
        List<PublicCourseSummary> publishedCourses
) {
    // Deliberately its own small shape rather than reusing courses.dto.CourseResponse — a public
    // profile card only needs enough to link to the course, not the full detail response.
    public record PublicCourseSummary(UUID id, String title, String thumbnailUrl, String category) {
        public static PublicCourseSummary from(Course course) {
            return new PublicCourseSummary(course.getId(), course.getTitle(), course.getThumbnailUrl(), course.getCategory());
        }
    }

    public static PublicProfileResponse from(User user, List<Course> publishedCourses) {
        return new PublicProfileResponse(
                user.getId(), user.getName(), user.getBio(), user.getAvatarUrl(), user.getRole(), user.getCreatedAt(),
                publishedCourses.stream().map(PublicCourseSummary::from).collect(Collectors.toList())
        );
    }
}
