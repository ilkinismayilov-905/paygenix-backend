package com.example.paygenix.controller;

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
    public ResponseEntity<Map<String,Object>> getAllUsers() {
        Map<String,Object> response = new HashMap<>();

        try {
            List<UserResponse> list = userService.getAll();

            response.put("success", true);
            response.put("data", list);

            return ResponseEntity.status(HttpStatus.OK).body(response);
        }catch (IllegalArgumentException e){
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
        catch (Exception e) {

            response.put("success", false);
            response.put("message", e.getMessage());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @PostMapping("/media")
    public ResponseEntity<Map<String,Object>> uploadMedia(@RequestParam("file") MultipartFile file) {
        Map<String,Object> response = new HashMap<>();
        try {
           String s = mediaService.upload(file);

            response.put("success", true);
            response.put("data", s);

            return ResponseEntity.ok(response);
        }catch (IllegalArgumentException e){
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
        catch (Exception e) {

            response.put("success", false);
            response.put("message", e.getMessage());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @GetMapping("/media-download")
    public ResponseEntity<Map<String,Object>> downloadMedia(@RequestParam String objectName) {
        Map<String,Object> response = new HashMap<>();
        try {
            String s = mediaService.getDownloadUrl(objectName);

            response.put("success", true);
            response.put("data", s);

            return ResponseEntity.ok(response);
        }catch (IllegalArgumentException e){
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
        catch (Exception e) {

            response.put("success", false);
            response.put("message", e.getMessage());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }


}
