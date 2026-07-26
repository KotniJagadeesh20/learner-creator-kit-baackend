package com.learncreator.progress.service;

import com.learncreator.auth.entity.User;
import com.learncreator.courses.entity.Course;
import com.learncreator.courses.entity.Lesson;
import com.learncreator.courses.repository.LessonRepository;
import com.learncreator.enrollments.entity.Enrollment;
import com.learncreator.enrollments.entity.EnrollmentStatus;
import com.learncreator.enrollments.repository.EnrollmentRepository;
import com.learncreator.progress.dto.CourseProgressResponse;
import com.learncreator.progress.dto.LessonProgressResponse;
import com.learncreator.progress.entity.LessonProgress;
import com.learncreator.progress.repository.LessonProgressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProgressService {

    private final LessonRepository lessonRepository;
    private final LessonProgressRepository lessonProgressRepository;
    private final EnrollmentRepository enrollmentRepository;

    @Transactional
    public CourseProgressResponse markLessonComplete(UUID lessonId, User learner) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lesson not found"));

        Course course = lesson.getModule().getCourse();

        Enrollment enrollment = enrollmentRepository.findByUserIdAndCourseId(learner.getId(), course.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN, "You must be enrolled in this course to track progress"));

        LessonProgress progress = lessonProgressRepository.findByEnrollmentIdAndLessonId(enrollment.getId(), lessonId)
                .orElseGet(() -> LessonProgress.builder().enrollment(enrollment).lesson(lesson).build());

        if (!progress.isCompleted()) {
            progress.setCompleted(true);
            progress.setCompletedAt(Instant.now());
            lessonProgressRepository.save(progress);
        }

        return buildCourseProgress(course, enrollment);
    }

    @Transactional
    public CourseProgressResponse getCourseProgress(UUID courseId, User learner) {
        Enrollment enrollment = enrollmentRepository.findByUserIdAndCourseId(learner.getId(), courseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "You are not enrolled in this course"));

        return buildCourseProgress(enrollment.getCourse(), enrollment);
    }

    private CourseProgressResponse buildCourseProgress(Course course, Enrollment enrollment) {
        List<Lesson> allLessons = lessonRepository.findByModule_Course_Id(course.getId());
        allLessons.sort(Comparator
                .comparing((Lesson l) -> l.getModule().getOrderIndex())
                .thenComparing(Lesson::getOrderIndex));

        Set<UUID> completedLessonIds = lessonProgressRepository.findByEnrollmentId(enrollment.getId())
                .stream()
                .filter(LessonProgress::isCompleted)
                .map(p -> p.getLesson().getId())
                .collect(Collectors.toSet());

        long completedCount = allLessons.stream().filter(l -> completedLessonIds.contains(l.getId())).count();
        int total = allLessons.size();

        // Auto-complete the enrollment the moment every lesson in the course is done.
        // Deliberately one-directional for v1: if a creator adds a new lesson to an already-
        // completed course, we don't automatically reopen it back to ACTIVE — that's a judgment
        // call worth revisiting later, not an oversight.
        boolean courseFullyComplete = total > 0 && completedCount == total;
        if (courseFullyComplete && enrollment.getStatus() != EnrollmentStatus.COMPLETED) {
            enrollment.setStatus(EnrollmentStatus.COMPLETED);
            enrollment.setCompletedAt(Instant.now());
            enrollmentRepository.save(enrollment);
        }

        int percentComplete = total == 0 ? 0 : (int) Math.round((completedCount * 100.0) / total);

        List<LessonProgressResponse> lessonResponses = allLessons.stream()
                .map(l -> new LessonProgressResponse(l.getId(), l.getTitle(), completedLessonIds.contains(l.getId())))
                .collect(Collectors.toList());

        return new CourseProgressResponse(
                course.getId(), total, (int) completedCount, percentComplete, enrollment.getStatus(), lessonResponses
        );
    }
}
