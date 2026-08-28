package com.learncreator.courses.repository;

import com.learncreator.courses.entity.Lesson;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LessonRepository extends JpaRepository<Lesson, UUID> {
    List<Lesson> findByModuleIdOrderByOrderIndexAsc(UUID moduleId);
    // Used by the progress module to compute completion across an entire course in one query,
    // rather than N+1 queries per module.
    List<Lesson> findByModule_Course_Id(UUID courseId);
}
