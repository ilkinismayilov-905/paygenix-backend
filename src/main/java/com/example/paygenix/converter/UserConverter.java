package com.example.paygenix.converter;

import com.example.paygenix.dto.response.UserResponse;
import com.example.paygenix.entity.User;
import com.example.paygenix.entity.UserPrincipal;
import org.springframework.stereotype.Component;

@Component
public class UserConverter {

    public UserResponse mapToUserInfo(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullname())
                .roles(user.getRoles())
                .active(user.isEnabled())
                .build();
    }

}
