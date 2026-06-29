package com.shrirang.distributed_promptforge.account_service.mapper;

import com.shrirang.distributed_promptforge.account_service.dto.auth.SignupRequest;
import com.shrirang.distributed_promptforge.account_service.dto.auth.UserProfileResponse;
import com.shrirang.distributed_promptforge.account_service.entity.User;
import com.mayur.distributed_promptforge.common_lib.dto.UserDto;
import com.mayur.distributed_promptforge.common_lib.security.JwtUserPrincipal;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public User toEntity(SignupRequest signupRequest) {
        if (signupRequest == null) {
            return null;
        }

        return User.builder()
                .username(signupRequest.username())
                .name(signupRequest.name())
                .password(signupRequest.password())
                .build();
    }

    public UserProfileResponse toUserProfileResponse(JwtUserPrincipal user) {
        if (user == null) {
            return null;
        }

        return new UserProfileResponse(
                user.userId(),
                user.username(),
                user.name(),
                user.role()
        );
    }

    public UserDto toUserDto(User user) {
        if (user == null) {
            return null;
        }

        return new UserDto(
                user.getId(),
                user.getUsername(),
                user.getName()
        );
    }

}
