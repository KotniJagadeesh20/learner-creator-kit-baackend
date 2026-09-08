package com.learncreator.seed;

import com.learncreator.auth.entity.Role;
import com.learncreator.auth.entity.User;
import com.learncreator.auth.repository.UserRepository;
import com.learncreator.courses.entity.Course;
import com.learncreator.courses.entity.CourseLevel;
import com.learncreator.courses.entity.CourseModule;
import com.learncreator.courses.entity.CourseStatus;
import com.learncreator.courses.entity.Lesson;
import com.learncreator.courses.repository.CourseRepository;
import com.learncreator.creator.entity.ApplicationStatus;
import com.learncreator.creator.entity.CreatorApplication;
import com.learncreator.creator.repository.CreatorApplicationRepository;
import com.learncreator.enrollments.entity.Enrollment;
import com.learncreator.enrollments.entity.EnrollmentStatus;
import com.learncreator.enrollments.repository.EnrollmentRepository;
import com.learncreator.progress.entity.LessonProgress;
import com.learncreator.progress.repository.LessonProgressRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Seeds realistic mock data for local testing — every login below uses the password "password123".
 *
 * NEVER runs by default. Only active when the "seed" Spring profile is enabled, e.g.:
 *   mvn spring-boot:run -Dspring-boot.run.profiles=seed
 *   or  SPRING_PROFILES_ACTIVE=seed  as an env var (see docker-compose override in the README)
 *
 * Idempotent: does nothing if any users already exist, so restarting the app with the seed
 * profile still active doesn't insert duplicates or fail on unique-constraint violations.
 */
@Component
@Profile("seed")
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final CourseRepository courseRepository;
    private final CreatorApplicationRepository creatorApplicationRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final LessonProgressRepository lessonProgressRepository;
    private final PasswordEncoder passwordEncoder;

    private static final String MOCK_PASSWORD = "password123";

    @Override
    public void run(String... args) {
        if (userRepository.count() > 0) {
            log.info("Seed data skipped — users already exist in the database.");
            return;
        }

        log.info("Seeding mock data (every account password is '{}')...", MOCK_PASSWORD);

        // ---- Users ----
        User admin = saveUser("Admin User", "admin@example.com", Role.ADMIN);
        User ashaCreator = saveUser("Asha Kumar", "asha@example.com", Role.CREATOR);
        User raviCreator = saveUser("Ravi Shah", "ravi@example.com", Role.CREATOR);
        User priyaLearner = saveUser("Priya Nair", "priya@example.com", Role.LEARNER);
        User samLearner = saveUser("Sam Verma", "sam@example.com", Role.LEARNER);

        // ---- Courses (built as a full object graph, cascade-saved through Course) ----
        Course reactCourse = buildCourse(
                ashaCreator, "React for Beginners",
                "Learn React from scratch — components, state, and hooks.",
                "tech", CourseLevel.BEGINNER, CourseStatus.PUBLISHED,
                List.of(
                        moduleWith("Getting Started", "Installing Node & npm", "Your First Component"),
                        moduleWith("State & Props", "useState Basics", "Passing Props", "Lifting State Up")
                )
        );
        courseRepository.save(reactCourse);

        Course javaCourse = buildCourse(
                ashaCreator, "Advanced Java (Work in Progress)",
                "Deep dive into concurrency and JVM internals.",
                "tech", CourseLevel.ADVANCED, CourseStatus.DRAFT, // still a draft — good for testing visibility rules
                List.of(moduleWith("Concurrency", "Threads vs Executors"))
        );
        courseRepository.save(javaCourse);

        Course marketingCourse = buildCourse(
                raviCreator, "Digital Marketing 101",
                "SEO, social media, and paid ads — the fundamentals.",
                "marketing", CourseLevel.BEGINNER, CourseStatus.PUBLISHED,
                List.of(moduleWith("Foundations", "What is SEO?", "Social Media Basics", "Intro to Paid Ads"))
        );
        courseRepository.save(marketingCourse);

        // ---- A pending creator application, so the admin approval UI has something to show ----
        CreatorApplication pendingApplication = CreatorApplication.builder()
                .user(samLearner)
                .pitch("I've spent 6 years in product design and want to teach UX fundamentals to beginners.")
                .status(ApplicationStatus.PENDING)
                .build();
        creatorApplicationRepository.save(pendingApplication);

        // ---- Enrollments + progress: give Priya a partially-done course and a fully-completed one ----
        Enrollment reactEnrollment = Enrollment.builder()
                .user(priyaLearner).course(reactCourse).status(EnrollmentStatus.ACTIVE).build();
        enrollmentRepository.save(reactEnrollment);

        Lesson firstReactLesson = reactCourse.getModules().get(0).getLessons().get(0);
        lessonProgressRepository.save(LessonProgress.builder()
                .enrollment(reactEnrollment).lesson(firstReactLesson).completed(true).completedAt(Instant.now()).build());

        Enrollment marketingEnrollment = Enrollment.builder()
                .user(priyaLearner).course(marketingCourse).status(EnrollmentStatus.COMPLETED)
                .completedAt(Instant.now()).build();
        enrollmentRepository.save(marketingEnrollment);

        for (Lesson lesson : marketingCourse.getModules().get(0).getLessons()) {
            lessonProgressRepository.save(LessonProgress.builder()
                    .enrollment(marketingEnrollment).lesson(lesson).completed(true).completedAt(Instant.now()).build());
        }

        log.info("Seed data complete: {} users, {} courses, 1 pending creator application, 2 enrollments.",
                userRepository.count(), courseRepository.count());
        log.info("Log in as any of: admin@example.com / asha@example.com / ravi@example.com / " +
                "priya@example.com / sam@example.com — password '{}' for all.", MOCK_PASSWORD);
    }

    private User saveUser(String name, String email, Role role) {
        User user = User.builder()
                .name(name)
                .email(email)
                .passwordHash(passwordEncoder.encode(MOCK_PASSWORD))
                .role(role)
                .build();
        return userRepository.save(user);
    }

    private Course buildCourse(User creator, String title, String description, String category,
                                CourseLevel level, CourseStatus status, List<CourseModule> modules) {
        Course course = Course.builder()
                .creator(creator)
                .title(title)
                .description(description)
                .category(category)
                .level(level)
                .status(status)
                .modules(new ArrayList<>())
                .build();

        int moduleIndex = 0;
        for (CourseModule module : modules) {
            module.setCourse(course);
            module.setOrderIndex(moduleIndex++);
            int lessonIndex = 0;
            for (Lesson lesson : module.getLessons()) {
                lesson.setModule(module);
                lesson.setOrderIndex(lessonIndex++);
                // No real videoRef — no Cloudflare video was actually uploaded for seed data.
                // The frontend already handles a null videoRef by disabling the play button.
                lesson.setDurationSeconds(300 + lessonIndex * 60);
            }
            course.getModules().add(module);
        }
        return course;
    }

    /** Builds an in-memory CourseModule with lessons, not yet attached to a course. */
    private CourseModule moduleWith(String moduleTitle, String... lessonTitles) {
        CourseModule module = CourseModule.builder().title(moduleTitle).lessons(new ArrayList<>()).build();
        for (String lessonTitle : lessonTitles) {
            module.getLessons().add(Lesson.builder().title(lessonTitle).build());
        }
        return module;
    }
}
