package com.learncreator.enrollments.service;

import com.learncreator.auth.entity.User;
import com.learncreator.courses.entity.Course;
import com.learncreator.courses.entity.CourseStatus;
import com.learncreator.courses.repository.CourseRepository;
import com.learncreator.enrollments.dto.EnrollmentResponse;
import com.learncreator.enrollments.entity.Enrollment;
import com.learncreator.enrollments.entity.EnrollmentStatus;
import com.learncreator.enrollments.repository.EnrollmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class EnrollmentService {

    private final EnrollmentRepository enrollmentRepository;
    private final CourseRepository courseRepository;

    public EnrollmentResponse enroll(UUID courseId, User learner) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found"));

        // A draft course shouldn't be enrollable — it may be incomplete or intentionally hidden.
        if (course.getStatus() != CourseStatus.PUBLISHED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This course is not currently available for enrollment");
        }

        if (enrollmentRepository.existsByUserIdAndCourseId(learner.getId(), courseId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You are already enrolled in this course");
        }

        Enrollment enrollment = Enrollment.builder()
                .user(learner)
                .course(course)
                .status(EnrollmentStatus.ACTIVE)
                .build();

        enrollmentRepository.save(enrollment);
        return EnrollmentResponse.from(enrollment);
    }

    public List<EnrollmentResponse> listMine(User learner) {
        return enrollmentRepository.findByUserId(learner.getId())
                .stream().map(EnrollmentResponse::from).collect(Collectors.toList());
    }

    public EnrollmentResponse getForCourse(UUID courseId, User learner) {
        Enrollment enrollment = enrollmentRepository.findByUserIdAndCourseId(learner.getId(), courseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "You are not enrolled in this course"));
        return EnrollmentResponse.from(enrollment);
    }
}
