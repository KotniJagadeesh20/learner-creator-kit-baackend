package com.learncreator.aitutor.service;

import com.learncreator.aitutor.dto.AskQuestionRequest;
import com.learncreator.aitutor.entity.*;
import com.learncreator.aitutor.repository.AiChatMessageRepository;
import com.learncreator.aitutor.repository.AiChatThreadRepository;
import com.learncreator.aitutor.repository.LessonTranscriptRepository;
import com.learncreator.auth.entity.Role;
import com.learncreator.auth.entity.User;
import com.learncreator.courses.entity.Course;
import com.learncreator.courses.entity.CourseModule;
import com.learncreator.courses.entity.Lesson;
import com.learncreator.courses.repository.LessonRepository;
import com.learncreator.enrollments.repository.EnrollmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiTutorChatServiceTest {

    @Mock private ChatClient chatClient;
    @Mock private VectorStore vectorStore;
    @Mock private LessonRepository lessonRepository;
    @Mock private LessonTranscriptRepository transcriptRepository;
    @Mock private AiChatThreadRepository threadRepository;
    @Mock private AiChatMessageRepository messageRepository;
    @Mock private EnrollmentRepository enrollmentRepository;

    // Mocks for ChatClient's fluent builder chain.
    @Mock private ChatClient.ChatClientRequestSpec requestSpec;
    @Mock private ChatClient.CallResponseSpec callResponseSpec;

    private AiTutorChatService service;

    private User learner;
    private Course course;
    private CourseModule module;
    private Lesson lesson;
    private AiChatThread thread;

    @BeforeEach
    void setUp() {
        service = new AiTutorChatService(
                chatClient, vectorStore, lessonRepository, transcriptRepository,
                threadRepository, messageRepository, enrollmentRepository
        );

        learner = User.builder().id(UUID.randomUUID()).role(Role.LEARNER).build();
        User creator = User.builder().id(UUID.randomUUID()).role(Role.CREATOR).build();
        course = Course.builder().id(UUID.randomUUID()).creator(creator).build();
        module = CourseModule.builder().id(UUID.randomUUID()).course(course).build();
        lesson = Lesson.builder().id(UUID.randomUUID()).module(module).title("useState Basics").build();
        thread = AiChatThread.builder().id(UUID.randomUUID()).user(learner).lesson(lesson).build();
    }

    private void stubChatClientToReturn(String content) {
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn(content);
    }

    @Test
    void askQuestion_rejects_whenLearnerNotEnrolled() {
        when(lessonRepository.findByIdWithContext(lesson.getId())).thenReturn(Optional.of(lesson));
        when(enrollmentRepository.existsByUserIdAndCourseId(learner.getId(), course.getId())).thenReturn(false);

        assertThatThrownBy(() -> service.askQuestion(lesson.getId(), new AskQuestionRequest("what is state?", false), learner))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.FORBIDDEN);

        verifyNoInteractions(chatClient);
    }

    @Test
    void askQuestion_defaultPath_answersFromTranscript_andCitesCurrentLesson() {
        when(lessonRepository.findByIdWithContext(lesson.getId())).thenReturn(Optional.of(lesson));
        when(enrollmentRepository.existsByUserIdAndCourseId(learner.getId(), course.getId())).thenReturn(true);
        when(threadRepository.findByUserIdAndLessonId(learner.getId(), lesson.getId())).thenReturn(Optional.of(thread));
        when(messageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LessonTranscript transcript = LessonTranscript.builder()
                .lesson(lesson).status(TranscriptStatus.READY).fullText("useState lets you add state to function components.")
                .build();
        when(transcriptRepository.findByLessonId(lesson.getId())).thenReturn(Optional.of(transcript));

        stubChatClientToReturn("useState is a Hook that lets you add state.");

        var result = service.askQuestion(lesson.getId(), new AskQuestionRequest("What is useState?", false), learner);

        assertThat(result).hasSize(2); // user message + assistant message
        assertThat(result.get(1).usedBroaderSearch()).isFalse();
        assertThat(result.get(1).sourceLessons()).containsExactly("useState Basics");
        assertThat(result.get(1).content()).contains("Answer based on:").contains("useState Basics");

        verifyNoInteractions(vectorStore); // default path must never hit the vector store
    }

    @Test
    void askQuestion_escalatesAutomatically_whenModelSignalsNotCovered() {
        when(lessonRepository.findByIdWithContext(lesson.getId())).thenReturn(Optional.of(lesson));
        when(enrollmentRepository.existsByUserIdAndCourseId(learner.getId(), course.getId())).thenReturn(true);
        when(threadRepository.findByUserIdAndLessonId(learner.getId(), lesson.getId())).thenReturn(Optional.of(thread));
        when(messageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LessonTranscript transcript = LessonTranscript.builder()
                .lesson(lesson).status(TranscriptStatus.READY).fullText("useState content only.")
                .build();
        when(transcriptRepository.findByLessonId(lesson.getId())).thenReturn(Optional.of(transcript));

        Document otherLessonChunk = new Document(
                "useReducer is for more complex state logic.",
                Map.of("lessonTitle", "Advanced Hooks")
        );
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(otherLessonChunk));

        // First call (default path) signals NOT_COVERED; second call (escalation) gives a real answer.
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content())
                .thenReturn("NOT_COVERED_BY_LESSON")
                .thenReturn("useReducer handles complex state transitions.");

        var result = service.askQuestion(lesson.getId(), new AskQuestionRequest("What is useReducer?", false), learner);

        assertThat(result.get(1).usedBroaderSearch()).isTrue();
        assertThat(result.get(1).sourceLessons()).containsExactly("Advanced Hooks");
    }

    @Test
    void askQuestion_forceBroaderSearch_skipsDefaultPathEntirely() {
        when(lessonRepository.findByIdWithContext(lesson.getId())).thenReturn(Optional.of(lesson));
        when(enrollmentRepository.existsByUserIdAndCourseId(learner.getId(), course.getId())).thenReturn(true);
        when(threadRepository.findByUserIdAndLessonId(learner.getId(), lesson.getId())).thenReturn(Optional.of(thread));
        when(messageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        var result = service.askQuestion(lesson.getId(), new AskQuestionRequest("something unrelated", true), learner);

        assertThat(result.get(1).content()).isEqualTo("This topic isn't covered in the current course.");
        assertThat(result.get(1).usedBroaderSearch()).isTrue();
        verify(transcriptRepository, never()).findByLessonId(any()); // never even checked the transcript
        verifyNoInteractions(chatClient); // no retrieved context means there is no reason to call the model
    }

    @Test
    void getHistory_rejects_whenLearnerNotEnrolled() {
        when(lessonRepository.findByIdWithContext(lesson.getId())).thenReturn(Optional.of(lesson));
        when(enrollmentRepository.existsByUserIdAndCourseId(learner.getId(), course.getId())).thenReturn(false);

        assertThatThrownBy(() -> service.getHistory(lesson.getId(), learner))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.FORBIDDEN);
    }

    @Test
    void getHistory_returnsEmptyList_whenNoThreadExistsYet() {
        when(lessonRepository.findByIdWithContext(lesson.getId())).thenReturn(Optional.of(lesson));
        when(enrollmentRepository.existsByUserIdAndCourseId(learner.getId(), course.getId())).thenReturn(true);
        when(threadRepository.findByUserIdAndLessonId(learner.getId(), lesson.getId())).thenReturn(Optional.empty());

        var result = service.getHistory(lesson.getId(), learner);

        assertThat(result).isEmpty();
        verifyNoInteractions(messageRepository);
    }
}
