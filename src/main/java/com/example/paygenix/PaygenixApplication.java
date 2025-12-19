package com.example.paygenix;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@SpringBootApplication
public class PaygenixApplication {

	public static void main(String[] args) {
		SpringApplication.run(PaygenixApplication.class, args);
	}

    @Component
    public class MailProbe {

        public MailProbe(JavaMailSender sender) {
            System.out.println("JavaMailSender FOUND ✅");
        }
    }

}
