package com.learncreator.courses.repository;

import com.learncreator.courses.entity.Lesson;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.UUID;
import java.util.Optional;

public interface LessonRepository extends JpaRepository<Lesson, UUID> {
    List<Lesson> findByModuleIdOrderByOrderIndexAsc(UUID moduleId);
    // Used by the progress module to compute completion across an entire course in one query,
    // rather than N+1 queries per module.
    List<Lesson> findByModule_Course_Id(UUID courseId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select lesson from Lesson lesson where lesson.id = :id")
    Optional<Lesson> findByIdForUpdate(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"module", "module.course"})
    @Query("select lesson from Lesson lesson where lesson.id = :id")
    Optional<Lesson> findByIdWithContext(@Param("id") UUID id);
}
