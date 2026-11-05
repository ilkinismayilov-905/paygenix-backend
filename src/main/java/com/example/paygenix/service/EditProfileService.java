package com.example.paygenix.service;

import com.example.paygenix.cache.CacheNames;
import com.example.paygenix.dto.request.EditProfileRequest;
import com.example.paygenix.dto.response.EditProfileResponse;
import com.example.paygenix.entity.EditProfile;
import com.example.paygenix.entity.User;
import com.example.paygenix.enums.MediaType;
import com.example.paygenix.exceptions.ApiException;
import com.example.paygenix.exceptions.NotFoundException;
import com.example.paygenix.mapper.EditProfileMapper;
import com.example.paygenix.media.ProfileMediaStorage;
import com.example.paygenix.media.StoryMediaStorage;
import com.example.paygenix.repository.EditProfileRepository;
import com.example.paygenix.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
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

    @Transactional()
    @CacheEvict(cacheNames = CacheNames.EDIT_PROFILE_ALL, allEntries = true)
    public EditProfileResponse editProfile(EditProfileRequest editProfile, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        EditProfile profile = editProfileRepository.findByEmail(email)
                .orElseGet(() -> EditProfile.builder()
                        .email(email)
                        .fullName(user.getDisplayName())
                        .build()
                );

        if (editProfile.fullName() != null) profile.setFullName(editProfile.fullName());
        if (editProfile.dateOfBirth() != null) profile.setDateOfBirth(editProfile.dateOfBirth());
        if (editProfile.gender() != null) {
            profile.setGender(editProfile.gender());
        } else {
            profile.setGender(profile.getGender());
        }
        if (editProfile.phoneNumber() != null) profile.setPhoneNumber(editProfile.phoneNumber());
        if (profile.getId() == null && (profile.getFullName() == null || profile.getPhoneNumber() == null
                || profile.getDateOfBirth() == null || profile.getGender() == null)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                    "A new profile needs fullName, phoneNumber, dateOfBirth and gender");
        }
        profile.setUser(user);

        user.setFullname(profile.getFullName());
        log.info("User data changed");

        userRepository.save(user);
        editProfileRepository.save(profile);
        log.info("UserProfile data changed");
        return mapper.toEditProfileResponse(profile);

    }

    @Transactional()
    @CacheEvict(cacheNames = CacheNames.EDIT_PROFILE_ALL, allEntries = true)
    public EditProfileResponse editProfilePhoto(
            Long userId,
            MultipartFile file) {
        String objectName = profileMediaStorage.uploadProfileImage(file, userId);
        User user = userService.getById(userId);
        EditProfile ep = editProfileRepository.findByEmail(user.getUsername())
                .orElseThrow(() -> new NotFoundException("Profile not found"));

        ep.setMediaType(MediaType.IMAGE);
        ep.setObjectName(objectName);
        editProfileRepository.save(ep);

        return mapper.toEditProfileResponse(ep);
    }

    // Not cached: the admin list cache is the only profile cache, and this read is one indexed row.
    @Transactional(readOnly = true)
    public EditProfileResponse getMine(User user) {
        return editProfileRepository.findByUserId(user.getId())
                .map(mapper::toEditProfileResponse)
                // no profile saved yet (fresh sign-up): what the account itself knows
                .orElseGet(() -> new EditProfileResponse(user.getDisplayName(), user.getEmail(), null, null, null, null));
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheNames.EDIT_PROFILE_ALL, key = "'all'")
    public List<EditProfileResponse> getAll() {
        log.info("Finding All UserProfile data");
        List<EditProfile> editProfiles = editProfileRepository.findAll();

        return mapper.toEditProfileResponseList(editProfiles);
    }
}
