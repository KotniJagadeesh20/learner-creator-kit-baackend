package com.learncreator.creator.service;

import com.learncreator.auth.entity.Role;
import com.learncreator.auth.entity.User;
import com.learncreator.auth.repository.UserRepository;
import com.learncreator.creator.dto.CreatorApplicationRequest;
import com.learncreator.creator.dto.CreatorApplicationResponse;
import com.learncreator.creator.entity.ApplicationStatus;
import com.learncreator.creator.entity.CreatorApplication;
import com.learncreator.creator.repository.CreatorApplicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CreatorApplicationService {

    private final CreatorApplicationRepository applicationRepository;
    private final UserRepository userRepository;

    public CreatorApplicationResponse apply(CreatorApplicationRequest request, User applicant) {
        if (applicant.getRole() != Role.LEARNER) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only learners can apply to become a creator");
        }
        if (applicationRepository.existsByUserIdAndStatus(applicant.getId(), ApplicationStatus.PENDING)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You already have a pending application");
        }

        CreatorApplication application = CreatorApplication.builder()
                .user(applicant)
                .pitch(request.pitch())
                .status(ApplicationStatus.PENDING)
                .build();

        applicationRepository.save(application);
        return CreatorApplicationResponse.from(application);
    }

    public List<CreatorApplicationResponse> myApplications(User user) {
        return applicationRepository.findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream().map(CreatorApplicationResponse::from).collect(Collectors.toList());
    }

    public List<CreatorApplicationResponse> listPending() {
        return applicationRepository.findByStatusOrderByCreatedAtAsc(ApplicationStatus.PENDING)
                .stream().map(CreatorApplicationResponse::from).collect(Collectors.toList());
    }

    public CreatorApplicationResponse approve(UUID applicationId, User admin) {
        CreatorApplication application = findPendingOrThrow(applicationId);

        application.setStatus(ApplicationStatus.APPROVED);
        application.setReviewedBy(admin);
        application.setReviewedAt(Instant.now());
        applicationRepository.save(application);

        // This is the actual moment a user gains creator powers — flip the role on the User row.
        User applicant = application.getUser();
        applicant.setRole(Role.CREATOR);
        userRepository.save(applicant);

        return CreatorApplicationResponse.from(application);
    }

    public CreatorApplicationResponse reject(UUID applicationId, String reason, User admin) {
        CreatorApplication application = findPendingOrThrow(applicationId);

        application.setStatus(ApplicationStatus.REJECTED);
        application.setRejectionReason(reason);
        application.setReviewedBy(admin);
        application.setReviewedAt(Instant.now());
        applicationRepository.save(application);

        return CreatorApplicationResponse.from(application);
    }

    private CreatorApplication findPendingOrThrow(UUID applicationId) {
        CreatorApplication application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found"));

        if (application.getStatus() != ApplicationStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This application has already been reviewed");
        }
        return application;
    }
}
