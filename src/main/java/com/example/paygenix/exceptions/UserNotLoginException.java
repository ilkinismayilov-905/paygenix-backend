package com.example.paygenix.exceptions;

public class UserNotLoginException extends RuntimeException {
    public UserNotLoginException(String message) {
        super(message);
    }
}
