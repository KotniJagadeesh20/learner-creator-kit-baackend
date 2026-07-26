package com.learncreator.courses.repository;

import com.learncreator.courses.entity.CourseModule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ModuleRepository extends JpaRepository<CourseModule, UUID> {
    List<CourseModule> findByCourseIdOrderByOrderIndexAsc(UUID courseId);
}
