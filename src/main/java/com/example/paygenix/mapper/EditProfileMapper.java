package com.example.paygenix.mapper;

import com.example.paygenix.dto.request.EditProfileRequest;
import com.example.paygenix.dto.request.EditProfileResponse;
import com.example.paygenix.entity.EditProfile;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.Instant;
import java.util.List;

@Mapper(componentModel = "spring",imports = Instant.class)
public interface EditProfileMapper {

    EditProfileResponse toEditProfileResponse(EditProfile editProfile);

    List<EditProfileResponse> toEditProfileResponseList(List<EditProfile> editProfiles);

    @Mapping(
            target = "createdAt",expression = "java(Instant.now())"
    )
    EditProfile toEditProfile(EditProfileRequest editProfile);
}
