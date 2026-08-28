package com.learncreator.aitutor.dto;

import com.learncreator.aitutor.entity.LessonTranscript;
import com.learncreator.aitutor.entity.TranscriptStatus;

public record TranscriptStatusResponse(
        TranscriptStatus status
) {
    public static TranscriptStatusResponse from(LessonTranscript transcript) {
        return new TranscriptStatusResponse(transcript.getStatus());
    }
}
