package com.example.paygenix.service;

import com.example.paygenix.cache.CacheNames;
import com.example.paygenix.dto.request.EditProfileRequest;
import com.example.paygenix.dto.response.EditProfileResponse;
import com.example.paygenix.entity.EditProfile;
import com.example.paygenix.entity.User;
import com.example.paygenix.enums.MediaType;
import com.example.paygenix.exceptions.NotFoundException;
import com.example.paygenix.mapper.EditProfileMapper;
import com.example.paygenix.media.ProfileMediaStorage;
import com.example.paygenix.media.StoryMediaStorage;
import com.example.paygenix.repository.EditProfileRepository;
import com.example.paygenix.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class EditProfileService {
    private final EditProfileRepository editProfileRepository;
    private final EditProfileMapper mapper;
    private final UserRepository userRepository;
    private final ProfileMediaStorage profileMediaStorage;
    private final UserService userService;

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheNames.EDIT_PROFILE_BY_ID, key = "#id")
    public EditProfileResponse findById(Long id) {
        log.info("Finding Edit Profile by id {}", id);
        EditProfile editProfile = editProfileRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User not found by id: " + id));
        return mapper.toEditProfileResponse(editProfile);
    }

    @Transactional()
    @Caching(put = {
            @CachePut(cacheNames = CacheNames.EDIT_PROFILE_BY_EMAIL, key = "#result.email"),
            @CachePut(cacheNames = CacheNames.EDIT_PROFILE_BY_ID, key = "#result.id")
    },
            evict = {
                    @CacheEvict(cacheNames = CacheNames.EDIT_PROFILE_ALL, allEntries = true),
                    @CacheEvict(cacheNames = CacheNames.EDIT_PROFILE_BY_ID, allEntries = true)
            })
    public EditProfileResponse editProfile(EditProfileRequest editProfile, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found by email: " + email));
        EditProfile profile = editProfileRepository.findByEmail(email)
                .orElseGet(() -> EditProfile.builder()
                        .email(email)
                        .fullName(user.getDisplayName())
                        .build()
                );

        System.out.println(user.getFullname());

        if (editProfile.email() != null) profile.setEmail(editProfile.email());
        if (editProfile.fullName() != null) profile.setFullName(editProfile.fullName());
        if (editProfile.dateOfBirth() != null) profile.setDateOfBirth(editProfile.dateOfBirth());
        if (editProfile.gender() != null) {
            profile.setGender(editProfile.gender());
        } else {
            profile.setGender(profile.getGender());
        }
        if (editProfile.phoneNumber() != null) profile.setPhoneNumber(editProfile.phoneNumber());
        profile.setUser(user);

        user.setEmail(profile.getEmail());
        user.setFullname(profile.getFullName());
        log.info("User data changed");

        userRepository.save(user);
        editProfileRepository.save(profile);
        log.info("UserProfile data changed");
        return mapper.toEditProfileResponse(profile);

    }

    @Transactional()
    @Caching(
            put = {
                    @CachePut(cacheNames = CacheNames.EDIT_PROFILE_BY_EMAIL, key = "#result.email"),
                    @CachePut(cacheNames = CacheNames.EDIT_PROFILE_BY_ID, key = "#result.id")
            },
            evict = {
                    @CacheEvict(cacheNames = CacheNames.EDIT_PROFILE_ALL, allEntries = true),
                    @CacheEvict(cacheNames = CacheNames.USER_BY_ID, key = "#userId")
            }
    )
    public EditProfileResponse editProfilePhoto(
            Long userId,
            MultipartFile file) {
        String objectName = profileMediaStorage.uploadProfileImage(file, userId);
        User user = userService.getById(userId);
        EditProfile ep = editProfileRepository.findByEmail(user.getUsername())
                .orElseThrow(() -> new NotFoundException("Profile not found by email: " + user.getUsername()));

        ep.setMediaType(MediaType.IMAGE);
        ep.setObjectName(objectName);
        editProfileRepository.save(ep);

        return mapper.toEditProfileResponse(ep);
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheNames.EDIT_PROFILE_ALL, key = "'all'")
    public List<EditProfileResponse> getAll() {
        log.info("Finding All UserProfile data");
        List<EditProfile> editProfiles = editProfileRepository.findAll();

        return mapper.toEditProfileResponseList(editProfiles);
    }
}
