package com.example.paygenix.controller;

import com.example.paygenix.dto.ApiResponse;
import com.example.paygenix.dto.response.UserResponse;
import com.example.paygenix.entity.Media;
import com.example.paygenix.repository.MediaRepository;
import com.example.paygenix.service.MediaService;
import com.example.paygenix.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
@CrossOrigin
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final MediaService mediaService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<UserResponse>>> getAllUsers() {
        List<UserResponse> list = userService.getAll();
        return ResponseEntity.ok(ApiResponse.success(list));
    }

    @PostMapping("/media")
    public ResponseEntity<ApiResponse<String>> uploadMedia(@RequestParam("file") MultipartFile file) {
        String s = mediaService.upload(file);
       return ResponseEntity.ok(ApiResponse.success(s));
    }

    @GetMapping("/media-download")
    public ResponseEntity<ApiResponse<String>> downloadMedia(@RequestParam String objectName) {
        String s = mediaService.getDownloadUrl(objectName);
        return ResponseEntity.ok(ApiResponse.success(s));
    }


}
