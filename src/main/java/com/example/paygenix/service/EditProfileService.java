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
import com.example.paygenix.repository.EditProfileRepository;
import com.example.paygenix.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
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
    private final TransactionTemplate transactionTemplate;

    static final long MAX_PHOTO_BYTES = 5L * 1024 * 1024;

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

    /**
     * Sets or replaces the photo. Needs a saved profile (409 PROFILE_REQUIRED), so a photo never waits for a profile
     * that may not come. The upload runs outside any transaction; the replaced photo is removed afterwards.
     */
    @CacheEvict(cacheNames = CacheNames.EDIT_PROFILE_ALL, allEntries = true)
    public EditProfileResponse editProfilePhoto(Long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.validation("file: is required");
        }
        if (file.getSize() > MAX_PHOTO_BYTES) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "PHOTO_TOO_LARGE", "The photo must be at most 5 MB");
        }
        if (editProfileRepository.findByUserId(userId).isEmpty()) {
            throw profileRequired();
        }
        String objectName = profileMediaStorage.uploadProfileImage(file, userId);
        String replaced;
        EditProfileResponse saved;
        try {
            String[] previous = new String[1];
            saved = transactionTemplate.execute(tx -> {
                EditProfile ep = editProfileRepository.findByUserIdForUpdate(userId).orElseThrow(EditProfileService::profileRequired);
                previous[0] = ep.getObjectName();
                ep.setMediaType(MediaType.IMAGE);
                ep.setObjectName(objectName);
                return mapper.toEditProfileResponse(editProfileRepository.save(ep));
            });
            replaced = previous[0];
        } catch (RuntimeException e) {
            profileMediaStorage.deleteQuietly(objectName);
            throw e;
        }
        if (replaced != null && replaced.startsWith("profiles/" + userId + "/")) {
            profileMediaStorage.deleteQuietly(replaced);
        }
        return withPhotoUrl(saved);
    }

    @CacheEvict(cacheNames = CacheNames.EDIT_PROFILE_ALL, allEntries = true)
    public void removeProfilePhoto(Long userId) {
        String removed = transactionTemplate.execute(tx -> editProfileRepository.findByUserIdForUpdate(userId).map(ep -> {
            String old = ep.getObjectName();
            ep.setObjectName(null);
            ep.setMediaType(null);
            return old;
        }).orElse(null));
        if (removed != null && removed.startsWith("profiles/" + userId + "/")) {
            profileMediaStorage.deleteQuietly(removed);
        }
    }

    private static ApiException profileRequired() {
        return new ApiException(HttpStatus.CONFLICT, "PROFILE_REQUIRED", "Save your profile before adding a photo");
    }

    // A storage outage must not hide the whole profile: the photo is just left out.
    private EditProfileResponse withPhotoUrl(EditProfileResponse r) {
        if (r.objectName() == null || !r.objectName().startsWith("profiles/")) {
            return r;
        }
        try {
            return r.withPhotoUrl(profileMediaStorage.presign(r.objectName()));
        } catch (ApiException e) {
            return r;
        }
    }

    // Not cached: the admin list cache is the only profile cache, and this read is one indexed row.
    @Transactional(readOnly = true)
    public EditProfileResponse getMine(User user) {
        return editProfileRepository.findByUserId(user.getId())
                .map(mapper::toEditProfileResponse)
                .map(this::withPhotoUrl)
                // no profile saved yet (fresh sign-up): what the account itself knows
                .orElseGet(() -> new EditProfileResponse(user.getDisplayName(), user.getEmail(), null, null, null, null, null));
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheNames.EDIT_PROFILE_ALL, key = "'all'")
    public List<EditProfileResponse> getAll() {
        log.info("Finding All UserProfile data");
        List<EditProfile> editProfiles = editProfileRepository.findAll();

        return mapper.toEditProfileResponseList(editProfiles);
    }
}
