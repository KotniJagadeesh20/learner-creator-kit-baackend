package com.learncreator.media.service;

import com.learncreator.auth.entity.Role;
import com.learncreator.auth.entity.User;
import com.learncreator.media.client.CloudflareDirectUploadApiResponse;
import com.learncreator.media.client.CloudflareStreamClient;
import com.learncreator.media.client.CloudflareVideoDetailApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaServiceTest {

    @Mock private CloudflareStreamClient cloudflareStreamClient;

    private MediaService mediaService;

    private User creator;
    private User learner;

    @BeforeEach
    void setUp() {
        mediaService = new MediaService(cloudflareStreamClient);
        creator = User.builder().id(UUID.randomUUID()).role(Role.CREATOR).build();
        learner = User.builder().id(UUID.randomUUID()).role(Role.LEARNER).build();
    }

    @Test
    void createUploadUrl_rejects_whenRequesterIsNotCreatorOrAdmin() {
        assertThatThrownBy(() -> mediaService.createUploadUrl(learner))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.FORBIDDEN);
    }

    @Test
    void createUploadUrl_returnsUploadUrlAndVideoId_forApprovedCreator() {
        var cloudflareResponse = new CloudflareDirectUploadApiResponse(
                true,
                new CloudflareDirectUploadApiResponse.Result("https://upload.cloudflarestream.com/abc", "video-123"),
                List.of()
        );
        when(cloudflareStreamClient.createDirectUpload()).thenReturn(cloudflareResponse);

        var result = mediaService.createUploadUrl(creator);

        assertThat(result.uploadUrl()).isEqualTo("https://upload.cloudflarestream.com/abc");
        assertThat(result.videoId()).isEqualTo("video-123");
    }

    @Test
    void getStatus_mapsReadyVideo_correctly() {
        var cloudflareResponse = new CloudflareVideoDetailApiResponse(
                true,
                new CloudflareVideoDetailApiResponse.Result(
                        "video-123",
                        true,
                        new CloudflareVideoDetailApiResponse.Status("ready", null),
                        "https://videodelivery.net/video-123/thumbnails/thumbnail.jpg",
                        new CloudflareVideoDetailApiResponse.Playback(
                                "https://videodelivery.net/video-123/manifest/video.m3u8",
                                "https://videodelivery.net/video-123/manifest/video.mpd"
                        ),
                        305.5
                ),
                List.of()
        );
        when(cloudflareStreamClient.getVideoDetail("video-123")).thenReturn(cloudflareResponse);

        var result = mediaService.getStatus("video-123", creator);

        assertThat(result.status()).isEqualTo("ready");
        assertThat(result.readyToStream()).isTrue();
        assertThat(result.hlsPlaybackUrl()).contains("manifest/video.m3u8");
        assertThat(result.durationSeconds()).isEqualTo(305.5);
    }

    @Test
    void getStatus_rejects_whenRequesterIsNotCreatorOrAdmin() {
        assertThatThrownBy(() -> mediaService.getStatus("video-123", learner))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.FORBIDDEN);
    }
}
