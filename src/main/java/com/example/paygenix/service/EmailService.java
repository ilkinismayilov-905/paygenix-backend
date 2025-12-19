package com.example.paygenix.service;

import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;


@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;


    public void sendOtp(String to, String otp) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject("Paygenix – OTP Verification");
        message.setText("Your verification code is: " + otp);

        mailSender.send(message);
    }
}

