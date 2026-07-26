package com.learncreator.courses.repository;

import com.learncreator.courses.entity.Course;
import com.learncreator.courses.entity.CourseStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CourseRepository extends JpaRepository<Course, UUID> {
    List<Course> findByStatus(CourseStatus status);
    List<Course> findByCreatorId(UUID creatorId);
}
