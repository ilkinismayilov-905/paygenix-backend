package com.example.paygenix.admin;

import java.time.LocalDateTime;
import java.util.List;

public record AdminUserItem(Long id, String username, String email, String fullName, String status, boolean verified,
                            List<String> roles, LocalDateTime createdAt) {
}
