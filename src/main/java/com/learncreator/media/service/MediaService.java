package com.learncreator.media.service;

import com.learncreator.auth.entity.Role;
import com.learncreator.auth.entity.User;
import com.learncreator.media.client.CloudflareStreamClient;
import com.learncreator.media.client.CloudflareVideoDetailApiResponse;
import com.learncreator.media.dto.UploadUrlResponse;
import com.learncreator.media.dto.VideoStatusResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class MediaService {

    private final CloudflareStreamClient cloudflareStreamClient;

    public UploadUrlResponse createUploadUrl(User requester) {
        requireCreatorRole(requester);

        var response = cloudflareStreamClient.createDirectUpload();
        return new UploadUrlResponse(response.result().uploadUrl(), response.result().uid());
    }

    public VideoStatusResponse getStatus(String videoId, User requester) {
        requireCreatorRole(requester);

        var response = cloudflareStreamClient.getVideoDetail(videoId);
        var result = response.result();

        String status = result.status() != null ? result.status().state() : "unknown";
        String errorMessage = result.status() != null ? result.status().errorReasonText() : null;

        return new VideoStatusResponse(
                result.uid(),
                status,
                result.readyToStream(),
                result.thumbnail(),
                result.playback() != null ? result.playback().hls() : null,
                result.playback() != null ? result.playback().dash() : null,
                result.duration(),
                errorMessage
        );
    }

    private void requireCreatorRole(User user) {
        if (user.getRole() != Role.CREATOR && user.getRole() != Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only approved creators can upload video");
        }
    }
}
