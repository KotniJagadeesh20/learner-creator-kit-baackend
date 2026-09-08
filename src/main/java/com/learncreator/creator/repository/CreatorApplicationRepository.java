package com.learncreator.creator.repository;

import com.learncreator.creator.entity.ApplicationStatus;
import com.learncreator.creator.entity.CreatorApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CreatorApplicationRepository extends JpaRepository<CreatorApplication, UUID> {
    List<CreatorApplication> findByUserIdOrderByCreatedAtDesc(UUID userId);
    List<CreatorApplication> findByStatusOrderByCreatedAtAsc(ApplicationStatus status);
    boolean existsByUserIdAndStatus(UUID userId, ApplicationStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select application from CreatorApplication application where application.id = :id")
    Optional<CreatorApplication> findByIdForUpdate(@Param("id") UUID id);
}
