package com.learncreator.aitutor.controller;

import com.learncreator.aitutor.dto.AskQuestionRequest;
import com.learncreator.aitutor.dto.ChatMessageResponse;
import com.learncreator.aitutor.dto.SetTranscriptRequest;
import com.learncreator.aitutor.dto.TranscriptStatusResponse;
import com.learncreator.aitutor.service.AiTutorChatService;
import com.learncreator.aitutor.service.TranscriptionService;
import com.learncreator.auth.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/ai-tutor")
@RequiredArgsConstructor
public class AiTutorController {

    private final AiTutorChatService chatService;
    private final TranscriptionService transcriptionService;

    @GetMapping("/lessons/{lessonId}/messages")
    public ResponseEntity<List<ChatMessageResponse>> getHistory(
            @PathVariable UUID lessonId,
            Authentication authentication
    ) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(chatService.getHistory(lessonId, user));
    }

    @PostMapping("/lessons/{lessonId}/messages")
    public ResponseEntity<List<ChatMessageResponse>> askQuestion(
            @PathVariable UUID lessonId,
            @Valid @RequestBody AskQuestionRequest request,
            Authentication authentication
    ) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(chatService.askQuestion(lessonId, request, user));
    }

    /**
     * Manual transcript entry — unchanged, still the direct/immediate path. Creator-only,
     * ownership-checked in the service.
     */
    @PutMapping("/lessons/{lessonId}/transcript")
    public ResponseEntity<Void> setTranscript(
            @PathVariable UUID lessonId,
            @Valid @RequestBody SetTranscriptRequest request,
            Authentication authentication
    ) {
        User user = (User) authentication.getPrincipal();
        transcriptionService.setManualTranscript(lessonId, request.transcriptText(), user);
        return ResponseEntity.noContent().build();
    }

    /**
     * Kicks off automatic transcription (Cloudflare audio extraction + Whisper). Runs in the
     * background — returns immediately; poll the status endpoint below to know when it's done.
     */
    @PostMapping("/lessons/{lessonId}/transcript/generate")
    public ResponseEntity<Void> generateTranscript(
            @PathVariable UUID lessonId,
            Authentication authentication
    ) {
        User user = (User) authentication.getPrincipal();
        transcriptionService.generateTranscriptAutomatically(lessonId, user);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    @GetMapping("/lessons/{lessonId}/transcript/status")
    public ResponseEntity<TranscriptStatusResponse> getTranscriptStatus(@PathVariable UUID lessonId) {
        return ResponseEntity.ok(TranscriptStatusResponse.from(transcriptionService.getTranscriptStatus(lessonId)));
    }
}
