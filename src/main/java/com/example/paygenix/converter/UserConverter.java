package com.example.paygenix.converter;

import com.example.paygenix.dto.response.UserResponse;
import com.example.paygenix.entity.User;
import com.example.paygenix.entity.UserPrincipal;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class UserConverter {

    public UserResponse mapToUserResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullname())
                .roles(user.getRoles())
                .active(user.isEnabled())
                .build();
    }

    public List<UserResponse> mapToUserResponseList(List<User> users) {
        return users.stream()
                .map(this::mapToUserResponse)
                .collect(Collectors.toList());
    }

}
