package com.learncreator.courses.service;

import com.learncreator.auth.entity.Role;
import com.learncreator.auth.entity.User;
import com.learncreator.courses.dto.*;
import com.learncreator.courses.entity.*;
import com.learncreator.courses.repository.CourseRepository;
import com.learncreator.courses.repository.LessonRepository;
import com.learncreator.courses.repository.ModuleRepository;
import com.learncreator.enrollments.repository.EnrollmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class CourseService {

    private final CourseRepository courseRepository;
    private final ModuleRepository moduleRepository;
    private final LessonRepository lessonRepository;
    private final EnrollmentRepository enrollmentRepository;

    // ---- Courses ----

    public List<CourseResponse> listPublished() {
        return courseRepository.findByStatus(CourseStatus.PUBLISHED)
                .stream().map(CourseResponse::summary).collect(Collectors.toList());
    }

    public List<CourseResponse> listMine(User creator) {
        return courseRepository.findByCreatorId(creator.getId())
                .stream().map(CourseResponse::summary).collect(Collectors.toList());
    }

    public CourseResponse getById(UUID courseId, User requester) {
        Course course = findCourseOrThrow(courseId);

        // Draft courses are only visible to their owner (or an admin) — not the public.
        if (course.getStatus() == CourseStatus.DRAFT && !isOwnerOrAdmin(course, requester)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found");
        }
        return isOwnerOrAdmin(course, requester) ? CourseResponse.from(course) : CourseResponse.publicDetails(course);
    }

    public CourseResponse create(CourseRequest request, User creator) {
        requireCreatorRole(creator);

        Course course = Course.builder()
                .creator(creator)
                .title(request.title())
                .description(request.description())
                .thumbnailUrl(request.thumbnailUrl())
                .category(request.category())
                .level(request.level())
                .status(CourseStatus.DRAFT)
                .build();

        courseRepository.save(course);
        return CourseResponse.from(course);
    }

    public CourseResponse update(UUID courseId, CourseRequest request, User requester) {
        Course course = findCourseOrThrow(courseId);
        requireOwnerOrAdmin(course, requester);

        course.setTitle(request.title());
        course.setDescription(request.description());
        course.setThumbnailUrl(request.thumbnailUrl());
        course.setCategory(request.category());
        course.setLevel(request.level());

        courseRepository.save(course);
        return CourseResponse.from(course);
    }

    public CourseResponse setStatus(UUID courseId, CourseStatus status, User requester) {
        Course course = findCourseOrThrow(courseId);
        requireOwnerOrAdmin(course, requester);

        if (status == CourseStatus.PUBLISHED && course.getModules().stream()
                .flatMap(module -> module.getLessons().stream()).findAny().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot publish a course with no lessons");
        }

        course.setStatus(status);
        courseRepository.save(course);
        return CourseResponse.from(course);
    }

    public void delete(UUID courseId, User requester) {
        Course course = findCourseOrThrow(courseId);
        requireOwnerOrAdmin(course, requester);
        courseRepository.delete(course);
    }

    // ---- Modules ----

    public ModuleResponse addModule(UUID courseId, ModuleRequest request, User requester) {
        Course course = findCourseOrThrow(courseId);
        requireOwnerOrAdmin(course, requester);

        CourseModule module = CourseModule.builder()
                .course(course)
                .title(request.title())
                .orderIndex(request.orderIndex())
                .build();

        moduleRepository.save(module);
        return ModuleResponse.from(module);
    }

    // ---- Lessons ----

    public LessonResponse addLesson(UUID moduleId, LessonRequest request, User requester) {
        CourseModule module = moduleRepository.findById(moduleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Module not found"));

        requireOwnerOrAdmin(module.getCourse(), requester);

        Lesson lesson = Lesson.builder()
                .module(module)
                .title(request.title())
                .videoRef(request.videoRef())
                .durationSeconds(request.durationSeconds())
                .orderIndex(request.orderIndex())
                .build();

        lessonRepository.save(lesson);
        return LessonResponse.from(lesson);
    }

    public LessonPlaybackResponse getLessonPlayback(UUID lessonId, User requester) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lesson not found"));
        Course course = lesson.getModule().getCourse();
        boolean enrolled = enrollmentRepository.existsByUserIdAndCourseId(requester.getId(), course.getId());
        if (!enrolled && !isOwnerOrAdmin(course, requester)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You must be enrolled to play this lesson");
        }
        return new LessonPlaybackResponse(lesson.getId(), lesson.getVideoRef());
    }

    // ---- Helpers ----

    private Course findCourseOrThrow(UUID courseId) {
        return courseRepository.findById(courseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found"));
    }

    private void requireCreatorRole(User user) {
        if (user.getRole() != Role.CREATOR && user.getRole() != Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only approved creators can create courses");
        }
    }

    private boolean isOwnerOrAdmin(Course course, User user) {
        return user != null && (course.getCreator().getId().equals(user.getId()) || user.getRole() == Role.ADMIN);
    }

    private void requireOwnerOrAdmin(Course course, User user) {
        if (!isOwnerOrAdmin(course, user)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not own this course");
        }
    }
}
