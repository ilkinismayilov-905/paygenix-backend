package com.example.paygenix.service;

import com.example.paygenix.converter.UserConverter;
import com.example.paygenix.dto.response.UserResponse;
import com.example.paygenix.entity.Media;
import com.example.paygenix.entity.User;
import com.example.paygenix.repository.MediaRepository;
import com.example.paygenix.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class UserService {
    private final UserRepository userRepository;
    private final UserConverter userConverter;
    private final MediaRepository mediaRepository;


    public List<UserResponse> getAll() {
        log.info("Getting all users");
        List<User> userResponseList = userRepository.findAll();
        return userConverter.mapToUserResponseList(userResponseList);
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
            e.printStackTrace();
            return new Media();
        }
    }


}
