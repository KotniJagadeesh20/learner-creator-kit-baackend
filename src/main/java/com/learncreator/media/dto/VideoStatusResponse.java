package com.learncreator.media.dto;

public record VideoStatusResponse(
        String videoId,
        // "queued" | "processing" | "ready" | "error" — mapped from Cloudflare's raw state
        String status,
        boolean readyToStream,
        String thumbnailUrl,
        String hlsPlaybackUrl,
        String dashPlaybackUrl,
        Double durationSeconds,
        String errorMessage
) {}
