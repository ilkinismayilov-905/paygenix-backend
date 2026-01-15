package com.example.paygenix.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// http://localhost:8080/oauth2/authorization/google

@RestController
@CrossOrigin
public class OAuth2Controller {
    @GetMapping("/oauth2/success")
    public ResponseEntity<?> success(@RequestParam String token) {
        return ResponseEntity.ok("TOKEN: " + token);
    }
}
