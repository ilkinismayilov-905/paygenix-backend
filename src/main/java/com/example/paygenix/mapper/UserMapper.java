package com.example.paygenix.mapper;

import com.example.paygenix.dto.response.UserResponse;
import com.example.paygenix.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(source = "fullname", target = "fullName")
    @Mapping(source = "enabled", target = "active")
    UserResponse userToUserResponse(User user);
    List<UserResponse> mapToUserResponseList(List<User> users);
}
