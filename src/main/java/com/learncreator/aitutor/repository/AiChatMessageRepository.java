package com.learncreator.aitutor.repository;

import com.learncreator.aitutor.entity.AiChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AiChatMessageRepository extends JpaRepository<AiChatMessage, UUID> {
    List<AiChatMessage> findByThreadIdOrderByCreatedAtAsc(UUID threadId);
}
