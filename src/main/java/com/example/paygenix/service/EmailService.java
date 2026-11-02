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

    // Sent instead of a code when someone starts sign-up with a registered email, so both cases cost one mail.
    public void sendAccountExists(String to) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject("Paygenix – you already have an account");
        message.setText("Someone tried to sign up with this email. If it was you, sign in instead; "
                + "if you forgot your password, reset it. If it was not you, you can ignore this message.");
        mailSender.send(message);
    }

    // Sent to the previous address after an email change, so a takeover does not go unnoticed.
    public void sendEmailChanged(String to) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject("Paygenix – your sign-in email was changed");
        message.setText("The sign-in email of your Paygenix account was changed and all devices were signed out. "
                + "If you did not do this, contact Paygenix support right away.");
        mailSender.send(message);
    }
}
