package com.learncreator.media.controller;

import com.learncreator.auth.entity.User;
import com.learncreator.media.dto.UploadUrlResponse;
import com.learncreator.media.dto.VideoStatusResponse;
import com.learncreator.media.service.MediaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/media")
@RequiredArgsConstructor
public class MediaController {

    private final MediaService mediaService;

    /**
     * Step 1 of the upload flow: get a one-time direct upload URL from Cloudflare.
     * The frontend then PUTs/POSTs the raw video file straight to that URL — the file
     * never passes through our backend.
     */
    @PostMapping("/upload-url")
    public ResponseEntity<UploadUrlResponse> createUploadUrl(Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(mediaService.createUploadUrl(user));
    }

    /**
     * Step 2: after the direct upload finishes, the frontend polls this to know when
     * transcoding is done and the video is actually playable.
     */
    @GetMapping("/{videoId}/status")
    public ResponseEntity<VideoStatusResponse> getStatus(
            @PathVariable String videoId,
            Authentication authentication
    ) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(mediaService.getStatus(videoId, user));
    }
}
