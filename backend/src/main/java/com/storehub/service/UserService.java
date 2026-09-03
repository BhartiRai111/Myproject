package com.storehub.service;

import com.storehub.dto.PagedResponse;
import com.storehub.dto.UserCreateRequest;
import com.storehub.dto.UserResponse;
import com.storehub.dto.UserStatusUpdateRequest;
import com.storehub.dto.UserUpdateRequest;
import com.storehub.entity.Role;
import com.storehub.entity.User;
import com.storehub.entity.UserStatus;
import com.storehub.exception.DuplicateEmailException;
import com.storehub.exception.UserNotFoundException;
import com.storehub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public PagedResponse<UserResponse> getUsers(String search, Role role, UserStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<UserResponse> result = userRepository.search(search, role, status, pageable)
                .map(UserResponse::fromEntity);
        return PagedResponse.fromPage(result);
    }

    public UserResponse getUserById(Long id) {
        return UserResponse.fromEntity(findUserOrThrow(id));
    }

    @Transactional
    public UserResponse createUser(UserCreateRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateEmailException(request.getEmail());
        }

        User user = User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .mobile(request.getMobile())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole())
                .status(request.getStatus() != null ? request.getStatus() : UserStatus.ACTIVE)
                .build();

        return UserResponse.fromEntity(userRepository.save(user));
    }

    @Transactional
    public UserResponse updateUser(Long id, UserUpdateRequest request) {
        User user = findUserOrThrow(id);

        if (!user.getEmail().equalsIgnoreCase(request.getEmail())
                && userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateEmailException(request.getEmail());
        }

        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setEmail(request.getEmail());
        user.setMobile(request.getMobile());
        user.setRole(request.getRole());
        user.setStatus(request.getStatus());

        return UserResponse.fromEntity(userRepository.save(user));
    }

    @Transactional
    public UserResponse updateStatus(Long id, UserStatusUpdateRequest request) {
        User user = findUserOrThrow(id);
        user.setStatus(request.getStatus());
        return UserResponse.fromEntity(userRepository.save(user));
    }

    private User findUserOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
    }
}
