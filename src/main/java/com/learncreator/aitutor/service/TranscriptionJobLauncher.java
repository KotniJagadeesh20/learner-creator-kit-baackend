package com.learncreator.aitutor.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class TranscriptionJobLauncher {

    private final ObjectProvider<TranscriptionService> transcriptionService;

    @Async("transcriptionExecutor")
    public void launch(UUID lessonId) {
        transcriptionService.getObject().processAutomaticTranscription(lessonId);
    }
}
