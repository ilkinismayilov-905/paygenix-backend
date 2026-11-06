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

    // Second code of an email change, sent to the address the account has now (T-106).
    public void sendEmailChangeCode(String to, String otp) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject("Paygenix – confirm changing your sign-in email");
        message.setText("Someone asked to change the sign-in email of your Paygenix account. Your code is: " + otp
                + "\nIf this was not you, do not share this code with anyone and change your PIN.");
        mailSender.send(message);
    }

    // Result of an identity review (T-070). The reason is the reviewer's text for the user.
    public void sendKycDecision(String to, boolean approved, String reason) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject(approved ? "Paygenix – your identity is verified" : "Paygenix – we could not verify your identity");
        message.setText(approved
                ? "We checked your document and your Paygenix account is now verified."
                : "We could not verify your identity with the document you sent: " + reason
                        + "\nOpen Paygenix and send a new photo of your document.");
        mailSender.send(message);
    }
}
