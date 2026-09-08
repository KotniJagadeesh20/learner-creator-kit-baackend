package com.learncreator.courses.service;

import com.learncreator.auth.entity.Role;
import com.learncreator.auth.entity.User;
import com.learncreator.courses.dto.CourseRequest;
import com.learncreator.courses.entity.Course;
import com.learncreator.courses.entity.CourseModule;
import com.learncreator.courses.entity.CourseStatus;
import com.learncreator.courses.repository.CourseRepository;
import com.learncreator.courses.repository.LessonRepository;
import com.learncreator.courses.repository.ModuleRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseServiceTest {

    @Mock private CourseRepository courseRepository;
    @Mock private ModuleRepository moduleRepository;
    @Mock private LessonRepository lessonRepository;
    @Mock private EnrollmentRepository enrollmentRepository;

    private CourseService courseService;

    private User creator;
    private User otherCreator;
    private User admin;
    private User learner;

    @BeforeEach
    void setUp() {
        courseService = new CourseService(courseRepository, moduleRepository, lessonRepository, enrollmentRepository);

        creator = User.builder().id(UUID.randomUUID()).role(Role.CREATOR).name("Creator One").build();
        otherCreator = User.builder().id(UUID.randomUUID()).role(Role.CREATOR).name("Creator Two").build();
        admin = User.builder().id(UUID.randomUUID()).role(Role.ADMIN).name("Admin").build();
        learner = User.builder().id(UUID.randomUUID()).role(Role.LEARNER).name("Learner").build();
    }

    @Test
    void create_rejects_whenUserIsNotCreatorOrAdmin() {
        CourseRequest request = new CourseRequest("Title", "Desc", null, "tech", null);

        assertThatThrownBy(() -> courseService.create(request, learner))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.FORBIDDEN);
    }

    @Test
    void create_succeeds_asDraft_forApprovedCreator() {
        CourseRequest request = new CourseRequest("React Basics", "Learn React", null, "tech", null);

        var response = courseService.create(request, creator);

        assertThat(response.status()).isEqualTo(CourseStatus.DRAFT); // every new course starts as DRAFT, never auto-published
        assertThat(response.title()).isEqualTo("React Basics");
    }

    @Test
    void update_rejects_whenRequesterIsNotOwnerOrAdmin() {
        Course course = courseOwnedBy(creator);
        when(courseRepository.findById(course.getId())).thenReturn(Optional.of(course));

        CourseRequest request = new CourseRequest("New Title", null, null, null, null);

        assertThatThrownBy(() -> courseService.update(course.getId(), request, otherCreator))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.FORBIDDEN);
    }

    @Test
    void update_succeeds_forAdmin_evenThoughNotOwner() {
        Course course = courseOwnedBy(creator);
        when(courseRepository.findById(course.getId())).thenReturn(Optional.of(course));

        CourseRequest request = new CourseRequest("Updated by admin", null, null, null, null);
        var response = courseService.update(course.getId(), request, admin);

        assertThat(response.title()).isEqualTo("Updated by admin");
    }

    @Test
    void getById_hidesDraftCourse_fromNonOwner() {
        Course draft = courseOwnedBy(creator);
        draft.setStatus(CourseStatus.DRAFT);
        when(courseRepository.findById(draft.getId())).thenReturn(Optional.of(draft));

        // otherCreator is not the owner and not admin — a draft must look like it doesn't exist to them
        assertThatThrownBy(() -> courseService.getById(draft.getId(), otherCreator))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.NOT_FOUND);
    }

    @Test
    void getById_hidesDraftCourse_fromAnonymousRequester() {
        Course draft = courseOwnedBy(creator);
        when(courseRepository.findById(draft.getId())).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> courseService.getById(draft.getId(), null))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.NOT_FOUND);
    }

    @Test
    void getById_showsDraftCourse_toOwner() {
        Course draft = courseOwnedBy(creator);
        draft.setStatus(CourseStatus.DRAFT);
        when(courseRepository.findById(draft.getId())).thenReturn(Optional.of(draft));

        var response = courseService.getById(draft.getId(), creator);
        assertThat(response.id()).isEqualTo(draft.getId());
    }

    @Test
    void setStatus_rejectsPublishing_whenCourseHasNoModules() {
        Course course = courseOwnedBy(creator); // no modules added
        when(courseRepository.findById(course.getId())).thenReturn(Optional.of(course));

        assertThatThrownBy(() -> courseService.setStatus(course.getId(), CourseStatus.PUBLISHED, creator))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("no lessons");
    }

    @Test
    void setStatus_allowsPublishing_whenCourseHasAtLeastOneModule() {
        Course course = courseOwnedBy(creator);
        CourseModule module = CourseModule.builder().id(UUID.randomUUID()).course(course).title("Intro")
                .lessons(new java.util.ArrayList<>()).build();
        module.getLessons().add(com.learncreator.courses.entity.Lesson.builder().module(module).title("Lesson").build());
        course.getModules().add(module);
        when(courseRepository.findById(course.getId())).thenReturn(Optional.of(course));

        var response = courseService.setStatus(course.getId(), CourseStatus.PUBLISHED, creator);
        assertThat(response.status()).isEqualTo(CourseStatus.PUBLISHED);
    }

    @Test
    void listPublished_onlyReturnsPublishedCourses() {
        when(courseRepository.findByStatus(CourseStatus.PUBLISHED))
                .thenReturn(List.of(courseOwnedBy(creator)));

        var result = courseService.listPublished();

        assertThat(result).hasSize(1);
    }

    private Course courseOwnedBy(User owner) {
        return Course.builder()
                .id(UUID.randomUUID())
                .creator(owner)
                .title("Sample Course")
                .status(CourseStatus.DRAFT)
                .modules(new java.util.ArrayList<>())
                .build();
    }
}
