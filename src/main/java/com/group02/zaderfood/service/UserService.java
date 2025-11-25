package com.group02.zaderfood.service;

import com.group02.zaderfood.dto.UserRegisterDTO;
import com.group02.zaderfood.entity.User;
import com.group02.zaderfood.entity.enums.UserRole;
import com.group02.zaderfood.entity.enums.UserStatus;
import com.group02.zaderfood.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    public void registerUser(UserRegisterDTO registerDTO) throws Exception {
        // 1. Kiểm tra Email đã tồn tại chưa
        if (userRepository.findByEmail(registerDTO.getEmail()).isPresent()) {
            throw new Exception("Email already exists!");
        }

        // 2. Kiểm tra mật khẩu nhập lại
        if (!registerDTO.getPassword().equals(registerDTO.getConfirmPassword())) {
            throw new Exception("Passwords do not match!");
        }

        // 3. Tạo User Entity bằng Builder (do script Python sinh ra có @Builder)
        User newUser = User.builder()
                .fullName(registerDTO.getUsername()) // Map Username form -> FullName DB
                .email(registerDTO.getEmail())
                .passwordHash(passwordEncoder.encode(registerDTO.getPassword())) // Mã hóa BCrypt
                .role(UserRole.USER)           // Dùng Enum: Mặc định là USER
                .status(UserStatus.ACTIVE)     // Dùng Enum: Mặc định là ACTIVE
                .isEmailVerified(false)
                .createdAt(LocalDateTime.now())
                .isDeleted(false)
                .build();

        // 4. Lưu vào Database
        userRepository.save(newUser);
    }
}