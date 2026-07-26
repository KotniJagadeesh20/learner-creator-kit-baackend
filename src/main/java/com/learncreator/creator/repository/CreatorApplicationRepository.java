package com.learncreator.creator.repository;

import com.learncreator.creator.entity.ApplicationStatus;
import com.learncreator.creator.entity.CreatorApplication;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CreatorApplicationRepository extends JpaRepository<CreatorApplication, UUID> {
    List<CreatorApplication> findByUserIdOrderByCreatedAtDesc(UUID userId);
    List<CreatorApplication> findByStatusOrderByCreatedAtAsc(ApplicationStatus status);
    boolean existsByUserIdAndStatus(UUID userId, ApplicationStatus status);
}
