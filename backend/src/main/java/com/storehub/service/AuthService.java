package com.storehub.service;

import com.storehub.dto.AuthResponse;
import com.storehub.dto.LoginRequest;
import com.storehub.dto.RegisterRequest;
import com.storehub.dto.UserResponse;
import com.storehub.entity.AuditAction;
import com.storehub.entity.Role;
import com.storehub.entity.User;
import com.storehub.entity.UserStatus;
import com.storehub.exception.BadRequestException;
import com.storehub.exception.DuplicateEmailException;
import com.storehub.exception.InvalidCredentialsException;
import com.storehub.repository.UserRepository;
import com.storehub.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuditService auditService;

    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new BadRequestException("Password and confirm password do not match");
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateEmailException(request.getEmail());
        }

        if (request.getRole() == Role.ADMIN) {
            throw new BadRequestException("Public registration cannot create an ADMIN account");
        }

        User user = User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .mobile(request.getMobile())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole())
                .status(UserStatus.ACTIVE)
                .build();

        User saved = userRepository.save(user);
        return UserResponse.fromEntity(saved);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail()).orElse(null);
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            auditService.log(AuditAction.LOGIN, "AUTH", "User", user != null ? user.getId() : null,
                    null, null, null, "Failed login attempt for " + request.getEmail() + ": invalid credentials");
            throw new InvalidCredentialsException("Invalid email or password");
        }

        if (user.getStatus() == UserStatus.INACTIVE) {
            auditService.log(AuditAction.LOGIN, "AUTH", "User", user.getId(), null,
                    null, null, "Failed login attempt for " + user.getEmail() + ": account is inactive");
            throw new InvalidCredentialsException("Your account is inactive. Please contact an administrator");
        }

        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);
        auditService.log(AuditAction.LOGIN, "AUTH", "User", user.getId(), null,
                null, null, "User " + user.getEmail() + " logged in");

        String token = jwtUtil.generateToken(user.getEmail(), user.getRole().name());

        return AuthResponse.builder()
                .token(token)
                .user(UserResponse.fromEntity(user))
                .build();
    }

    public UserResponse getCurrentUser(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("User not found"));
        return UserResponse.fromEntity(user);
    }
}
