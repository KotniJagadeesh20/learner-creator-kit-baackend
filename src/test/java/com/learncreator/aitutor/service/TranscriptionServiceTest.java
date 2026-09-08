package com.learncreator.aitutor.service;

import com.learncreator.aitutor.entity.LessonTranscript;
import com.learncreator.aitutor.entity.TranscriptStatus;
import com.learncreator.aitutor.repository.LessonTranscriptRepository;
import com.learncreator.auth.entity.Role;
import com.learncreator.auth.entity.User;
import com.learncreator.courses.entity.Course;
import com.learncreator.courses.entity.CourseModule;
import com.learncreator.courses.entity.Lesson;
import com.learncreator.courses.repository.LessonRepository;
import com.learncreator.media.client.CloudflareStreamClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.ai.model.Model;
import org.springframework.http.HttpStatus;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TranscriptionServiceTest {

    @Mock private LessonTranscriptRepository transcriptRepository;
    @Mock private LessonRepository lessonRepository;
    @Mock private EmbeddingIndexService embeddingIndexService;
    @Mock private CloudflareStreamClient cloudflareStreamClient;
    @Mock private Model<AudioTranscriptionPrompt, AudioTranscriptionResponse> audioTranscriptionModel;
    @Mock private TranscriptionJobLauncher transcriptionJobLauncher;

    private TranscriptionService service;

    private User creator;
    private User otherCreator;
    private Course course;
    private CourseModule module;
    private Lesson lesson;

    @BeforeEach
    void setUp() {
        service = new TranscriptionService(
                transcriptRepository, lessonRepository, embeddingIndexService,
                cloudflareStreamClient, audioTranscriptionModel, transcriptionJobLauncher
        );

        creator = User.builder().id(UUID.randomUUID()).role(Role.CREATOR).build();
        otherCreator = User.builder().id(UUID.randomUUID()).role(Role.CREATOR).build();
        course = Course.builder().id(UUID.randomUUID()).creator(creator).build();
        module = CourseModule.builder().id(UUID.randomUUID()).course(course).build();
        lesson = Lesson.builder().id(UUID.randomUUID()).module(module).title("Intro").videoRef("cf-video-123").build();
    }

    // ---- Manual entry: unchanged behavior, still covered ----

    @Test
    void setManualTranscript_rejects_whenRequesterDoesNotOwnCourse() {
        when(lessonRepository.findById(lesson.getId())).thenReturn(Optional.of(lesson));

        assertThatThrownBy(() -> service.setManualTranscript(lesson.getId(), "some text", otherCreator))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.FORBIDDEN);

        verifyNoInteractions(embeddingIndexService);
    }

    @Test
    void setManualTranscript_savesAndIndexes_forOwner() {
        when(lessonRepository.findById(lesson.getId())).thenReturn(Optional.of(lesson));
        when(transcriptRepository.findByLessonId(lesson.getId())).thenReturn(Optional.empty());

        service.setManualTranscript(lesson.getId(), "Welcome to this lesson...", creator);

        ArgumentCaptor<LessonTranscript> captor = ArgumentCaptor.forClass(LessonTranscript.class);
        verify(transcriptRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(TranscriptStatus.READY);
        assertThat(captor.getValue().getFullText()).isEqualTo("Welcome to this lesson...");
        verify(embeddingIndexService).indexTranscript(lesson, "Welcome to this lesson...");
    }

    // ---- Automatic generation: fast-failing paths only (no test waits on the real poll loop) ----

    @Test
    void generateTranscriptAutomatically_rejects_whenRequesterDoesNotOwnCourse() {
        when(lessonRepository.findByIdForUpdate(lesson.getId())).thenReturn(Optional.of(lesson));

        assertThatThrownBy(() -> service.generateTranscriptAutomatically(lesson.getId(), otherCreator))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.FORBIDDEN);

        verifyNoInteractions(cloudflareStreamClient);
    }

    @Test
    void generateTranscriptAutomatically_rejects_whenLessonHasNoVideo() {
        Lesson lessonWithNoVideo = Lesson.builder().id(UUID.randomUUID()).module(module).title("No video yet").build();
        when(lessonRepository.findByIdForUpdate(lessonWithNoVideo.getId())).thenReturn(Optional.of(lessonWithNoVideo));
        assertThatThrownBy(() -> service.generateTranscriptAutomatically(lessonWithNoVideo.getId(), creator))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.BAD_REQUEST);
        verifyNoInteractions(cloudflareStreamClient); // never even attempted Cloudflare — failed before that
    }

    @Test
    void generateTranscriptAutomatically_marksFailed_whenExecutorRejectsJob() {
        LessonTranscript transcript = LessonTranscript.builder().lesson(lesson).status(TranscriptStatus.PENDING).build();
        when(lessonRepository.findByIdForUpdate(lesson.getId())).thenReturn(Optional.of(lesson));
        when(transcriptRepository.findByLessonId(lesson.getId())).thenReturn(Optional.of(transcript));
        doThrow(new TaskRejectedException("queue full")).when(transcriptionJobLauncher).launch(lesson.getId());

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.generateTranscriptAutomatically(lesson.getId(), creator);
            TransactionSynchronizationManager.getSynchronizations().forEach(synchronization -> synchronization.afterCommit());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        assertThat(transcript.getStatus()).isEqualTo(TranscriptStatus.FAILED);
        verify(transcriptRepository, atLeast(2)).save(transcript);
    }

    @Test
    void processAutomaticTranscription_marksFailed_whenCloudflareRequestFails() {
        LessonTranscript transcript = LessonTranscript.builder().lesson(lesson).status(TranscriptStatus.PROCESSING).build();
        when(lessonRepository.findByIdWithContext(lesson.getId())).thenReturn(Optional.of(lesson));
        when(transcriptRepository.findByLessonId(lesson.getId())).thenReturn(Optional.of(transcript));
        // Fails on the very first Cloudflare call, before the polling loop ever sleeps.
        when(cloudflareStreamClient.requestAudioDownload("cf-video-123"))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Cloudflare unavailable"));

        service.processAutomaticTranscription(lesson.getId());

        assertThat(transcript.getStatus()).isEqualTo(TranscriptStatus.FAILED);
        verify(transcriptRepository).save(transcript);
        verifyNoInteractions(audioTranscriptionModel); // never got as far as calling Whisper
    }

    @Test
    void getTranscriptStatus_rejects_whenNoTranscriptExists() {
        UUID lessonId = UUID.randomUUID();
        when(transcriptRepository.findByLessonId(lessonId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getTranscriptStatus(lessonId))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.NOT_FOUND);
    }
}
