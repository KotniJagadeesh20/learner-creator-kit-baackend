package com.learncreator.users.service;

import com.learncreator.auth.entity.Role;
import com.learncreator.auth.entity.User;
import com.learncreator.auth.repository.UserRepository;
import com.learncreator.courses.entity.Course;
import com.learncreator.courses.entity.CourseStatus;
import com.learncreator.courses.repository.CourseRepository;
import com.learncreator.users.dto.PublicProfileResponse;
import com.learncreator.users.dto.UpdateProfileRequest;
import com.learncreator.users.dto.UserProfileResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final CourseRepository courseRepository;

    public UserProfileResponse getMyProfile(User user) {
        return UserProfileResponse.from(user);
    }

    public UserProfileResponse updateMyProfile(UpdateProfileRequest request, User user) {
        user.setName(request.name());
        user.setBio(request.bio());
        user.setAvatarUrl(request.avatarUrl());
        userRepository.save(user);
        return UserProfileResponse.from(user);
    }

    public PublicProfileResponse getPublicProfile(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        // Only surface published courses on a public profile — a draft is invisible everywhere
        // else (see courses module), so it must stay invisible here too, even to the public.
        List<Course> publishedCourses = user.getRole() == Role.CREATOR || user.getRole() == Role.ADMIN
                ? courseRepository.findByCreatorId(userId).stream()
                        .filter(c -> c.getStatus() == CourseStatus.PUBLISHED)
                        .toList()
                : List.of();

        return PublicProfileResponse.from(user, publishedCourses);
    }
}
