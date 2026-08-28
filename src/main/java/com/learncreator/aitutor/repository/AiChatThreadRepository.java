package com.learncreator.aitutor.repository;

import com.learncreator.aitutor.entity.AiChatThread;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AiChatThreadRepository extends JpaRepository<AiChatThread, UUID> {
    Optional<AiChatThread> findByUserIdAndLessonId(UUID userId, UUID lessonId);
}
