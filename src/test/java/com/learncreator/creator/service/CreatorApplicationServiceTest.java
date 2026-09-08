package com.learncreator.creator.service;

import com.learncreator.auth.entity.Role;
import com.learncreator.auth.entity.User;
import com.learncreator.auth.repository.UserRepository;
import com.learncreator.creator.dto.CreatorApplicationRequest;
import com.learncreator.creator.entity.ApplicationStatus;
import com.learncreator.creator.entity.CreatorApplication;
import com.learncreator.creator.repository.CreatorApplicationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreatorApplicationServiceTest {

    @Mock private CreatorApplicationRepository applicationRepository;
    @Mock private UserRepository userRepository;

    private CreatorApplicationService service;

    private User learner;
    private User admin;

    @BeforeEach
    void setUp() {
        service = new CreatorApplicationService(applicationRepository, userRepository);
        learner = User.builder().id(UUID.randomUUID()).role(Role.LEARNER).name("Asha").build();
        admin = User.builder().id(UUID.randomUUID()).role(Role.ADMIN).name("Admin").build();
    }

    @Test
    void apply_rejects_whenUserIsAlreadyCreator() {
        User existingCreator = User.builder().id(UUID.randomUUID()).role(Role.CREATOR).build();
        var request = new CreatorApplicationRequest("I want to teach SQL fundamentals to beginners.");

        assertThatThrownBy(() -> service.apply(request, existingCreator))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.CONFLICT);
    }

    @Test
    void apply_rejects_whenUserAlreadyHasPendingApplication() {
        when(applicationRepository.existsByUserIdAndStatus(learner.getId(), ApplicationStatus.PENDING))
                .thenReturn(true);
        var request = new CreatorApplicationRequest("I want to teach SQL fundamentals to beginners.");

        assertThatThrownBy(() -> service.apply(request, learner))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already have a pending application");
    }

    @Test
    void apply_succeeds_forEligibleLearner() {
        when(applicationRepository.existsByUserIdAndStatus(learner.getId(), ApplicationStatus.PENDING))
                .thenReturn(false);
        var request = new CreatorApplicationRequest("I want to teach SQL fundamentals to beginners.");

        var response = service.apply(request, learner);

        assertThat(response.status()).isEqualTo(ApplicationStatus.PENDING);
        assertThat(response.userId()).isEqualTo(learner.getId());
    }

    @Test
    void approve_flipsApplicantRoleToCreator_inSameOperation() {
        CreatorApplication application = CreatorApplication.builder()
                .id(UUID.randomUUID())
                .user(learner)
                .pitch("pitch")
                .status(ApplicationStatus.PENDING)
                .build();
        when(applicationRepository.findByIdForUpdate(application.getId())).thenReturn(Optional.of(application));

        service.approve(application.getId(), admin);

        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.APPROVED);
        assertThat(application.getReviewedBy()).isEqualTo(admin);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getRole()).isEqualTo(Role.CREATOR); // the actual privilege change
    }

    @Test
    void approve_rejects_whenApplicationAlreadyReviewed() {
        CreatorApplication alreadyApproved = CreatorApplication.builder()
                .id(UUID.randomUUID())
                .user(learner)
                .pitch("pitch")
                .status(ApplicationStatus.APPROVED) // already reviewed once
                .build();
        when(applicationRepository.findByIdForUpdate(alreadyApproved.getId())).thenReturn(Optional.of(alreadyApproved));

        assertThatThrownBy(() -> service.approve(alreadyApproved.getId(), admin))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already been reviewed");

        verify(userRepository, never()).save(any()); // role must NOT change on a rejected re-review attempt
    }

    @Test
    void reject_setsReasonAndStatus_withoutChangingRole() {
        CreatorApplication application = CreatorApplication.builder()
                .id(UUID.randomUUID())
                .user(learner)
                .pitch("pitch")
                .status(ApplicationStatus.PENDING)
                .build();
        when(applicationRepository.findByIdForUpdate(application.getId())).thenReturn(Optional.of(application));

        service.reject(application.getId(), "Not enough detail on teaching experience", admin);

        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.REJECTED);
        assertThat(application.getRejectionReason()).isEqualTo("Not enough detail on teaching experience");
        verify(userRepository, never()).save(any()); // rejection must never touch the user's role
    }
}
