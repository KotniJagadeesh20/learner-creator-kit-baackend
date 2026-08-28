package com.learncreator.users.service;

import com.learncreator.auth.entity.Role;
import com.learncreator.auth.entity.User;
import com.learncreator.auth.repository.UserRepository;
import com.learncreator.courses.entity.Course;
import com.learncreator.courses.entity.CourseStatus;
import com.learncreator.courses.repository.CourseRepository;
import com.learncreator.users.dto.UpdateProfileRequest;
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
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private CourseRepository courseRepository;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, courseRepository);
    }

    @Test
    void updateMyProfile_updatesNameBioAndAvatar() {
        User user = User.builder().id(UUID.randomUUID()).name("Old Name").role(Role.LEARNER).build();
        var request = new UpdateProfileRequest("New Name", "New bio", "https://example.com/avatar.png");

        var response = userService.updateMyProfile(request, user);

        assertThat(response.name()).isEqualTo("New Name");
        assertThat(response.bio()).isEqualTo("New bio");
        assertThat(user.getName()).isEqualTo("New Name"); // entity itself was mutated, not just the response
    }

    @Test
    void getPublicProfile_rejects_whenUserDoesNotExist() {
        UUID missingId = UUID.randomUUID();
        when(userRepository.findById(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getPublicProfile(missingId))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.NOT_FOUND);
    }

    @Test
    void getPublicProfile_onlyIncludesPublishedCourses_forCreators() {
        User creator = User.builder().id(UUID.randomUUID()).name("Asha").role(Role.CREATOR).build();
        Course published = Course.builder().id(UUID.randomUUID()).creator(creator).title("Published One").status(CourseStatus.PUBLISHED).build();
        Course draft = Course.builder().id(UUID.randomUUID()).creator(creator).title("Draft One").status(CourseStatus.DRAFT).build();

        when(userRepository.findById(creator.getId())).thenReturn(Optional.of(creator));
        when(courseRepository.findByCreatorId(creator.getId())).thenReturn(List.of(published, draft));

        var response = userService.getPublicProfile(creator.getId());

        assertThat(response.publishedCourses()).hasSize(1);
        assertThat(response.publishedCourses().get(0).title()).isEqualTo("Published One");
    }

    @Test
    void getPublicProfile_returnsNoCourses_forLearners() {
        User learner = User.builder().id(UUID.randomUUID()).name("Priya").role(Role.LEARNER).build();
        when(userRepository.findById(learner.getId())).thenReturn(Optional.of(learner));

        var response = userService.getPublicProfile(learner.getId());

        assertThat(response.publishedCourses()).isEmpty();
    }
}
