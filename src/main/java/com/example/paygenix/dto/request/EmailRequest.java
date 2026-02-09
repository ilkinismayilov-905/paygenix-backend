package com.example.paygenix.dto.request;

import jakarta.validation.constraints.Email;

public record EmailRequest(@Email String email) {
}
