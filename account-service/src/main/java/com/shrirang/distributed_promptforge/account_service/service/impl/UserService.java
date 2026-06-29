package com.shrirang.distributed_promptforge.account_service.service.impl;

import com.shrirang.distributed_promptforge.account_service.entity.User;
import com.shrirang.distributed_promptforge.account_service.repository.UserRepository;
import com.mayur.distributed_promptforge.common_lib.security.JwtUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@RequiredArgsConstructor
@Service
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsernameIgnoreCase(username.trim())
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        if (Boolean.TRUE.equals(user.getBlocked())) {
            throw new DisabledException("User account is blocked");
        }

        if (Boolean.FALSE.equals(user.getEmailVerified())) {
            throw new DisabledException("Email not verified");
        }

        return new JwtUserPrincipal(
                user.getId(),
                user.getName(),
                user.getUsername(),
                user.getRole() != null ? user.getRole().name() : "USER",
                user.getPassword(),
                List.of(new SimpleGrantedAuthority("ROLE_" + (user.getRole() != null ? user.getRole().name() : "USER")))
        );    }
}
