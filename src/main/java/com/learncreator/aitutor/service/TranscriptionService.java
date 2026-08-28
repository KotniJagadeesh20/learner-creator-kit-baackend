package com.learncreator.aitutor.service;

import com.learncreator.aitutor.entity.LessonTranscript;
import com.learncreator.aitutor.entity.TranscriptStatus;
import com.learncreator.aitutor.repository.LessonTranscriptRepository;
import com.learncreator.auth.entity.Role;
import com.learncreator.auth.entity.User;
import com.learncreator.courses.entity.Lesson;
import com.learncreator.courses.repository.LessonRepository;
import com.learncreator.media.client.CloudflareDownloadsApiResponse;
import com.learncreator.media.client.CloudflareStreamClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.ai.model.Model;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TranscriptionService {

    private final LessonTranscriptRepository transcriptRepository;
    private final LessonRepository lessonRepository;
    private final EmbeddingIndexService embeddingIndexService;
    private final CloudflareStreamClient cloudflareStreamClient;
    // Spring AI's OpenAI starter auto-configures this bean from spring.ai.openai.api-key —
    // no manual @Bean needed, same as EmbeddingModel/VectorStore.
    private final Model<AudioTranscriptionPrompt, AudioTranscriptionResponse> audioTranscriptionModel;

    private static final int MAX_POLL_ATTEMPTS = 40; // ~10 minutes at 15s intervals — generous for a typical lesson video
    private static final long POLL_INTERVAL_MS = 15_000;

    /**
     * Manual transcript entry — a stopgap for creators who'd rather paste a transcript directly,
     * or a fallback if automatic generation fails. Kept exactly as originally built; automatic
     * generation (below) is an addition, not a replacement.
     */
    public void setManualTranscript(UUID lessonId, String transcriptText, User requester) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lesson not found"));

        requireOwnerOrAdmin(lesson, requester);

        LessonTranscript transcript = transcriptRepository.findByLessonId(lessonId)
                .orElseGet(() -> LessonTranscript.builder().lesson(lesson).build());

        saveAndIndex(lesson, transcript, transcriptText);
    }

    /**
     * Kicks off automatic transcription for a lesson's uploaded video: extracts audio via
     * Cloudflare's /downloads/audio endpoint, polls until it's ready, sends the audio to OpenAI's
     * Whisper API, then saves and indexes the result exactly like a manual entry would.
     *
     * Runs asynchronously (@Async) — the triggering HTTP request returns immediately with
     * status PROCESSING; the frontend polls getTranscriptStatus() to know when it's done.
     */
    @Async
    public void generateTranscriptAutomatically(UUID lessonId, User requester) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lesson not found"));
        requireOwnerOrAdmin(lesson, requester);

        if (lesson.getVideoRef() == null) {
            markFailed(lesson, "Lesson has no uploaded video yet");
            return;
        }

        LessonTranscript transcript = transcriptRepository.findByLessonId(lessonId)
                .orElseGet(() -> LessonTranscript.builder().lesson(lesson).build());
        transcript.setStatus(TranscriptStatus.PROCESSING);
        transcriptRepository.save(transcript);

        try {
            String audioUrl = extractAudioUrl(lesson.getVideoRef());
            byte[] audioBytes = downloadAudio(audioUrl);
            String transcriptText = transcribeAudio(audioBytes, lesson.getTitle());

            saveAndIndex(lesson, transcript, transcriptText);
            log.info("Automatic transcript generated for lessonId={}", lessonId);

        } catch (Exception e) {
            log.error("Automatic transcript generation failed for lessonId={}", lessonId, e);
            markFailed(lesson, e.getMessage());
        }
    }

    public LessonTranscript getTranscriptStatus(UUID lessonId) {
        return transcriptRepository.findByLessonId(lessonId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No transcript exists for this lesson yet"));
    }

    // ---- Cloudflare audio extraction (the part that was previously blocked) ----

    private String extractAudioUrl(String videoId) throws InterruptedException {
        cloudflareStreamClient.requestAudioDownload(videoId);

        for (int attempt = 0; attempt < MAX_POLL_ATTEMPTS; attempt++) {
            Thread.sleep(POLL_INTERVAL_MS);

            CloudflareDownloadsApiResponse status = cloudflareStreamClient.getDownloads(videoId);
            CloudflareDownloadsApiResponse.DownloadInfo audio = status.result().audio();

            if (audio != null && "ready".equals(audio.status())) {
                return audio.url();
            }
        }
        throw new IllegalStateException("Timed out waiting for Cloudflare to finish extracting audio");
    }

    private byte[] downloadAudio(String audioUrl) {
        // Plain, unauthenticated GET — this is a public delivery URL, not a Cloudflare API call,
        // so it deliberately does NOT reuse cloudflareRestClient (which carries our API bearer
        // token — that token has no business being sent to a different domain).
        return RestClient.create()
                .get()
                .uri(audioUrl)
                .retrieve()
                .body(byte[].class);
    }

    private String transcribeAudio(byte[] audioBytes, String lessonTitleForFilename) {
        var resource = new ByteArrayResource(audioBytes) {
            @Override
            public String getFilename() {
                // Some providers use the filename to infer format — .m4a matches what Cloudflare produced.
                return lessonTitleForFilename.replaceAll("[^a-zA-Z0-9]", "_") + ".m4a";
            }
        };

        AudioTranscriptionResponse response = audioTranscriptionModel.call(new AudioTranscriptionPrompt(resource));
        return response.getResult().getOutput();
    }

    // ---- Shared with manual entry ----

    private void saveAndIndex(Lesson lesson, LessonTranscript transcript, String transcriptText) {
        transcript.setFullText(transcriptText);
        transcript.setStatus(TranscriptStatus.READY);
        transcriptRepository.save(transcript);

        embeddingIndexService.indexTranscript(lesson, transcriptText);
    }

    private void markFailed(Lesson lesson, String reason) {
        LessonTranscript transcript = transcriptRepository.findByLessonId(lesson.getId())
                .orElseGet(() -> LessonTranscript.builder().lesson(lesson).build());
        transcript.setStatus(TranscriptStatus.FAILED);
        transcriptRepository.save(transcript);
        log.warn("Transcript generation marked FAILED for lessonId={}: {}", lesson.getId(), reason);
    }

    private void requireOwnerOrAdmin(Lesson lesson, User requester) {
        boolean isOwner = lesson.getModule().getCourse().getCreator().getId().equals(requester.getId());
        if (!isOwner && requester.getRole() != Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not own this lesson's course");
        }
    }
}
