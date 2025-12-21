package com.example.paygenix.service;

import com.example.paygenix.converter.UserConverter;
import com.example.paygenix.dto.response.UserResponse;
import com.example.paygenix.entity.User;
import com.example.paygenix.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class UserService {
    private final UserRepository userRepository;
    private final UserConverter userConverter;

    public List<UserResponse> getAll() {
        log.info("Getting all users");
        List<User> userResponseList = userRepository.findAll();
        return userConverter.mapToUserResponseList(userResponseList);
    }


}
