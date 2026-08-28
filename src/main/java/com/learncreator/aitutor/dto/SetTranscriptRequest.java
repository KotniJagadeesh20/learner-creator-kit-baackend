package com.learncreator.aitutor.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Manual transcript entry — a stopgap until automatic generation (Whisper, triggered after
 * Cloudflare upload) is built. See AITUTOR.md section 12 for the unresolved detail blocking that:
 * how to pull an audio-extractable file out of Cloudflare Stream for Whisper to consume.
 */
public record SetTranscriptRequest(
        @NotBlank(message = "transcriptText is required")
        String transcriptText
) {}
