package com.learncreator.aitutor.repository;

import com.learncreator.aitutor.entity.LessonTranscript;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LessonTranscriptRepository extends JpaRepository<LessonTranscript, UUID> {
    Optional<LessonTranscript> findByLessonId(UUID lessonId);
}
