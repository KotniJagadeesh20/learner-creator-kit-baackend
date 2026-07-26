package com.learncreator.progress.service;

import com.learncreator.auth.entity.Role;
import com.learncreator.auth.entity.User;
import com.learncreator.courses.entity.Course;
import com.learncreator.courses.entity.CourseModule;
import com.learncreator.courses.entity.CourseStatus;
import com.learncreator.courses.entity.Lesson;
import com.learncreator.courses.repository.LessonRepository;
import com.learncreator.enrollments.entity.Enrollment;
import com.learncreator.enrollments.entity.EnrollmentStatus;
import com.learncreator.enrollments.repository.EnrollmentRepository;
import com.learncreator.progress.entity.LessonProgress;
import com.learncreator.progress.repository.LessonProgressRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProgressServiceTest {

    @Mock private LessonRepository lessonRepository;
    @Mock private LessonProgressRepository lessonProgressRepository;
    @Mock private EnrollmentRepository enrollmentRepository;

    private ProgressService progressService;

    private User learner;
    private User creator;
    private Course course;
    private CourseModule module;
    private Lesson lessonOne;
    private Lesson lessonTwo;
    private Enrollment enrollment;

    @BeforeEach
    void setUp() {
        progressService = new ProgressService(lessonRepository, lessonProgressRepository, enrollmentRepository);

        learner = User.builder().id(UUID.randomUUID()).role(Role.LEARNER).build();
        creator = User.builder().id(UUID.randomUUID()).role(Role.CREATOR).build();
        course = Course.builder().id(UUID.randomUUID()).creator(creator).status(CourseStatus.PUBLISHED).build();
        module = CourseModule.builder().id(UUID.randomUUID()).course(course).orderIndex(0).build();
        lessonOne = Lesson.builder().id(UUID.randomUUID()).module(module).title("Intro").orderIndex(0).build();
        lessonTwo = Lesson.builder().id(UUID.randomUUID()).module(module).title("Next Steps").orderIndex(1).build();
        enrollment = Enrollment.builder()
                .id(UUID.randomUUID()).user(learner).course(course).status(EnrollmentStatus.ACTIVE).build();
    }

    @Test
    void markLessonComplete_rejects_whenLearnerNotEnrolled() {
        when(lessonRepository.findById(lessonOne.getId())).thenReturn(Optional.of(lessonOne));
        when(enrollmentRepository.findByUserIdAndCourseId(learner.getId(), course.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> progressService.markLessonComplete(lessonOne.getId(), learner))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.FORBIDDEN);
    }

    @Test
    void markLessonComplete_marksOneOfTwoLessons_courseNotYetComplete() {
        when(lessonRepository.findById(lessonOne.getId())).thenReturn(Optional.of(lessonOne));
        when(enrollmentRepository.findByUserIdAndCourseId(learner.getId(), course.getId())).thenReturn(Optional.of(enrollment));
        when(lessonProgressRepository.findByEnrollmentIdAndLessonId(enrollment.getId(), lessonOne.getId()))
                .thenReturn(Optional.empty());
        when(lessonRepository.findByModule_Course_Id(course.getId())).thenReturn(List.of(lessonOne, lessonTwo));
        when(lessonProgressRepository.findByEnrollmentId(enrollment.getId())).thenReturn(List.of(
                LessonProgress.builder().lesson(lessonOne).completed(true).build()
        ));

        var response = progressService.markLessonComplete(lessonOne.getId(), learner);

        assertThat(response.totalLessons()).isEqualTo(2);
        assertThat(response.completedLessons()).isEqualTo(1);
        assertThat(response.percentComplete()).isEqualTo(50);
        assertThat(response.enrollmentStatus()).isEqualTo(EnrollmentStatus.ACTIVE); // not done yet
        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.ACTIVE); // entity itself untouched
    }

    @Test
    void markLessonComplete_autoCompletesEnrollment_whenAllLessonsDone() {
        when(lessonRepository.findById(lessonTwo.getId())).thenReturn(Optional.of(lessonTwo));
        when(enrollmentRepository.findByUserIdAndCourseId(learner.getId(), course.getId())).thenReturn(Optional.of(enrollment));
        when(lessonProgressRepository.findByEnrollmentIdAndLessonId(enrollment.getId(), lessonTwo.getId()))
                .thenReturn(Optional.empty());
        when(lessonRepository.findByModule_Course_Id(course.getId())).thenReturn(List.of(lessonOne, lessonTwo));
        // Both lessons now show as completed (lessonOne was already done in a prior call, lessonTwo just got marked)
        when(lessonProgressRepository.findByEnrollmentId(enrollment.getId())).thenReturn(List.of(
                LessonProgress.builder().lesson(lessonOne).completed(true).build(),
                LessonProgress.builder().lesson(lessonTwo).completed(true).build()
        ));

        var response = progressService.markLessonComplete(lessonTwo.getId(), learner);

        assertThat(response.percentComplete()).isEqualTo(100);
        assertThat(response.enrollmentStatus()).isEqualTo(EnrollmentStatus.COMPLETED);
        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.COMPLETED); // the actual entity was flipped
        assertThat(enrollment.getCompletedAt()).isNotNull();
    }

    @Test
    void markLessonComplete_isIdempotent_doesNotResetCompletedAtOnRepeatCalls() {
        LessonProgress alreadyDone = LessonProgress.builder()
                .lesson(lessonOne).completed(true).completedAt(java.time.Instant.parse("2024-01-01T00:00:00Z")).build();

        when(lessonRepository.findById(lessonOne.getId())).thenReturn(Optional.of(lessonOne));
        when(enrollmentRepository.findByUserIdAndCourseId(learner.getId(), course.getId())).thenReturn(Optional.of(enrollment));
        when(lessonProgressRepository.findByEnrollmentIdAndLessonId(enrollment.getId(), lessonOne.getId()))
                .thenReturn(Optional.of(alreadyDone));
        when(lessonRepository.findByModule_Course_Id(course.getId())).thenReturn(List.of(lessonOne));
        when(lessonProgressRepository.findByEnrollmentId(enrollment.getId())).thenReturn(List.of(alreadyDone));

        progressService.markLessonComplete(lessonOne.getId(), learner);

        // completedAt should be untouched — the "already completed" branch must not re-save/re-stamp it
        assertThat(alreadyDone.getCompletedAt()).isEqualTo(java.time.Instant.parse("2024-01-01T00:00:00Z"));
    }

    @Test
    void getCourseProgress_rejects_whenNotEnrolled() {
        when(enrollmentRepository.findByUserIdAndCourseId(learner.getId(), course.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> progressService.getCourseProgress(course.getId(), learner))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.NOT_FOUND);
    }
}
