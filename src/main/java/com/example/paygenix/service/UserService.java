package com.example.paygenix.service;


import com.example.paygenix.mapper.UserMapper;
import com.example.paygenix.dto.response.UserResponse;
import com.example.paygenix.entity.Media;
import com.example.paygenix.entity.User;
import com.example.paygenix.exceptions.NotFoundException;
import com.example.paygenix.exceptions.PinAlreadySetException;
import com.example.paygenix.repository.MediaRepository;
import com.example.paygenix.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class UserService {
    private final UserRepository userRepository;
    private final MediaRepository mediaRepository;
    private final UserMapper mapper;
    private final PasswordEncoder pinEncoder;


    public List<UserResponse> getAll() {
        log.info("Getting all users");
        List<User> userResponseList = userRepository.findAll();
        return mapper.mapToUserResponseList(userResponseList);
    }

    public User getById(Long id) {
        log.info("Getting user by id: {}", id);
        User user = userRepository.findById(id)
                .orElseThrow(()-> new UsernameNotFoundException("User not found"));
        return user;
    }

    public User getByUsername(String username) {
        log.info("Getting user by username: {}", username);
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        return user;
    }

    public User getByEmail(String email) {
        log.info("Getting user by email: {}", email);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        return user;
    }


    public Media addMedia(MultipartFile mediaFile) {
        Media media = new Media();
        try {
            String filename = System.currentTimeMillis() + "_" + StringUtils.cleanPath(mediaFile.getOriginalFilename());
            String uploadDir = "/uploads/" + filename;
            Path filePath = Paths.get(uploadDir, filename);
            Files.createDirectories(filePath.getParent());
            mediaFile.transferTo(filePath.toFile());

            String mediaUrl = "/media/" + filename;
            String contentType = mediaFile.getContentType();
            String mediaType = null;
            if (contentType != null) {
                if (contentType.startsWith("image/")) mediaType = "image";
                else if (contentType.startsWith("audio/")) mediaType = "audio";
                else if (contentType.startsWith("video/")) mediaType = "video";
            }

            media.setMediaUrl(mediaUrl);
            media.setMediaType(mediaType);
            media.setUploadedDate(LocalDateTime.now());

            mediaRepository.save(media);

            return media;
        } catch (IOException e) {
            throw new UncheckedIOException("Media upload failed", e);
        }
    }

    // Google accounts start without a PIN (decision #12); the first one is set here, later ones via the change-PIN flow.
    public void setFirstPin(Long userId, String pin) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        if (user.getPinHash() != null) {
            throw new PinAlreadySetException();
        }
        user.setPinHash(pinEncoder.encode(pin));
    }
}
