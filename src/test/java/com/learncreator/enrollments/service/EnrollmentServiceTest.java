package com.learncreator.enrollments.service;

import com.learncreator.auth.entity.Role;
import com.learncreator.auth.entity.User;
import com.learncreator.courses.entity.Course;
import com.learncreator.courses.entity.CourseStatus;
import com.learncreator.courses.repository.CourseRepository;
import com.learncreator.enrollments.repository.EnrollmentRepository;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EnrollmentServiceTest {

    @Mock private EnrollmentRepository enrollmentRepository;
    @Mock private CourseRepository courseRepository;

    private EnrollmentService enrollmentService;

    private User learner;
    private User creator;

    @BeforeEach
    void setUp() {
        enrollmentService = new EnrollmentService(enrollmentRepository, courseRepository);
        learner = User.builder().id(UUID.randomUUID()).role(Role.LEARNER).name("Learner").build();
        creator = User.builder().id(UUID.randomUUID()).role(Role.CREATOR).name("Creator").build();
    }

    @Test
    void enroll_rejects_whenCourseIsNotPublished() {
        Course draft = Course.builder().id(UUID.randomUUID()).creator(creator).status(CourseStatus.DRAFT).build();
        when(courseRepository.findById(draft.getId())).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> enrollmentService.enroll(draft.getId(), learner))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.BAD_REQUEST);
    }

    @Test
    void enroll_rejects_whenAlreadyEnrolled() {
        Course published = Course.builder().id(UUID.randomUUID()).creator(creator).status(CourseStatus.PUBLISHED).build();
        when(courseRepository.findById(published.getId())).thenReturn(Optional.of(published));
        when(enrollmentRepository.existsByUserIdAndCourseId(learner.getId(), published.getId())).thenReturn(true);

        assertThatThrownBy(() -> enrollmentService.enroll(published.getId(), learner))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.CONFLICT);
    }

    @Test
    void enroll_succeeds_forPublishedCourse_notYetEnrolled() {
        Course published = Course.builder()
                .id(UUID.randomUUID()).creator(creator).title("React Basics").status(CourseStatus.PUBLISHED).build();
        when(courseRepository.findById(published.getId())).thenReturn(Optional.of(published));
        when(enrollmentRepository.existsByUserIdAndCourseId(learner.getId(), published.getId())).thenReturn(false);

        var response = enrollmentService.enroll(published.getId(), learner);

        assertThat(response.courseId()).isEqualTo(published.getId());
        assertThat(response.courseTitle()).isEqualTo("React Basics");
    }

    @Test
    void enroll_rejects_whenCourseDoesNotExist() {
        UUID missingId = UUID.randomUUID();
        when(courseRepository.findById(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> enrollmentService.enroll(missingId, learner))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.NOT_FOUND);
    }

    @Test
    void listMine_returnsOnlyThatUsersEnrollments() {
        when(enrollmentRepository.findByUserId(learner.getId())).thenReturn(List.of());
        var result = enrollmentService.listMine(learner);
        assertThat(result).isEmpty();
    }
}
