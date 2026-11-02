package com.example.paygenix.service;

import com.example.paygenix.mapper.UserMapper;
import com.example.paygenix.dto.response.UserResponse;
import com.example.paygenix.entity.User;
import com.example.paygenix.exceptions.NotFoundException;
import com.example.paygenix.exceptions.PinAlreadySetException;
import com.example.paygenix.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class UserService {
    private final UserRepository userRepository;
    private final UserMapper mapper;
    private final PasswordEncoder pinEncoder;


    public List<UserResponse> getAll() {
        log.info("Getting all users");
        List<User> userResponseList = userRepository.findAll();
        return mapper.mapToUserResponseList(userResponseList);
    }

    public User getById(Long id) {
        log.info("Getting user by id: {}", id);
        User user = userRepository.findById(id)
                .orElseThrow(()-> new UsernameNotFoundException("User not found"));
        return user;
    }

    public User getByEmail(String email) {
        log.debug("Getting user by email");
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        return user;
    }


    // Google accounts start without a PIN (decision #12); the first one is set here, later ones via the change-PIN flow.
    public void setFirstPin(Long userId, String pin) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        if (user.getPinHash() != null) {
            throw new PinAlreadySetException();
        }
        user.setPinHash(pinEncoder.encode(pin));
    }
}
