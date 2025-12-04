package com.group02.zaderfood.service;

import com.group02.zaderfood.dto.ChangePasswordDTO;
import com.group02.zaderfood.dto.UserRegisterDTO;
import com.group02.zaderfood.entity.User;
import com.group02.zaderfood.entity.enums.UserRole;
import com.group02.zaderfood.entity.enums.UserStatus;
import com.group02.zaderfood.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

// profile
import com.group02.zaderfood.dto.UserProfileDTO;
import com.group02.zaderfood.entity.UserDietaryPreference;
import com.group02.zaderfood.entity.UserProfile;
import com.group02.zaderfood.entity.enums.DietType;
import com.group02.zaderfood.repository.UserDietaryPreferenceRepository;
import com.group02.zaderfood.repository.UserProfileRepository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private UserDietaryPreferenceRepository dietRepo;

    public boolean isEmailExists(String email) {
        return userRepository.findByEmail(email).isPresent();
    }

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
                .role(UserRole.USER) // Dùng Enum: Mặc định là USER
                .status(UserStatus.ACTIVE) // Dùng Enum: Mặc định là ACTIVE
                .isEmailVerified(true)
                .createdAt(LocalDateTime.now())
                .isDeleted(false)
                .build();

        // 4. Lưu vào Database
        userRepository.save(newUser);
    }

    // 1. Lấy thông tin (Sửa: Dùng userId)
    public UserProfileDTO getUserProfile(Integer userId) throws Exception {
        // Tìm user để lấy Email và FullName (nếu cần hiển thị lại)
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new Exception("User not found"));

        // Tìm profile
        UserProfile profile = userProfileRepository.findByUserId(userId)
                .orElse(new UserProfile());

        UserProfileDTO dto = new UserProfileDTO();

        // Map dữ liệu từ User Entity
        dto.setEmail(user.getEmail());
        dto.setFullName(user.getFullName());

        List<UserDietaryPreference> dietList = dietRepo.findByUserId(userId);
        List<DietType> dietTypes = dietList.stream()
                .map(UserDietaryPreference::getDietType)
                .collect(Collectors.toList());

        // Map dữ liệu từ UserProfile Entity
        dto.setWeightKg(profile.getWeightKg());
        dto.setHeightCm(profile.getHeightCm());
        dto.setBirthDate(profile.getBirthDate());
        dto.setGender(profile.getGender());
        dto.setActivityLevel(profile.getActivityLevel());
        dto.setCalorieGoalPerDay(profile.getCalorieGoalPerDay());
        dto.setDietaryPreferences(dietTypes);
        dto.setAllergies(profile.getAllergies());

        return dto;
    }

    @Transactional
    public void updateUserProfile(Integer userId, UserProfileDTO dto) throws Exception {
        // 1. Cập nhật bảng Users
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new Exception("User not found"));
        user.setFullName(dto.getFullName());
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        // 2. Cập nhật bảng UserProfiles
        // Kiểm tra xem profile đã tồn tại chưa
        Optional<UserProfile> optionalProfile = userProfileRepository.findByUserId(userId);

        UserProfile profile;
        if (optionalProfile.isPresent()) {
            // Case A: Đã có -> Cập nhật (Hibernate sẽ tự hiểu là Update)
            profile = optionalProfile.get();
        } else {
            // Case B: Chưa có -> Tạo mới (Cần set ID thủ công)
            profile = new UserProfile();
            profile.setUserId(userId); // Quan trọng: Gán ID của User cho Profile
            profile.setCreatedAt(LocalDateTime.now());
            profile.setIsDeleted(false);
        }

        // Map dữ liệu
        profile.setWeightKg(dto.getWeightKg());
        profile.setHeightCm(dto.getHeightCm());
        profile.setBirthDate(dto.getBirthDate());
        profile.setGender(dto.getGender());
        profile.setActivityLevel(dto.getActivityLevel());
        profile.setCalorieGoalPerDay(dto.getCalorieGoalPerDay());
        profile.setAllergies(dto.getAllergies());
        profile.setUpdatedAt(LocalDateTime.now());

        userProfileRepository.save(profile);
        dietRepo.deleteByUserId(userId);

        if (dto.getDietaryPreferences() != null && !dto.getDietaryPreferences().isEmpty()) {
            List<UserDietaryPreference> newDiets = new ArrayList<>();
            for (DietType type : dto.getDietaryPreferences()) {
                UserDietaryPreference newDiet = UserDietaryPreference.builder()
                        .userId(userId)
                        .dietType(type)
                        .createdAt(LocalDateTime.now())
                        .build();
                newDiets.add(newDiet);
            }
            dietRepo.saveAll(newDiets);
        }
    }

    @Transactional
    public void changePassword(Integer userId, ChangePasswordDTO dto) throws Exception {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new Exception("User not found"));

        // 1. Kiểm tra mật khẩu hiện tại có đúng không
        if (!passwordEncoder.matches(dto.getCurrentPassword(), user.getPasswordHash())) {
            throw new Exception("Mật khẩu hiện tại không đúng!");
        }

        // 2. Kiểm tra mật khẩu mới và xác nhận có khớp không
        if (!dto.getNewPassword().equals(dto.getConfirmPassword())) {
            throw new Exception("Mật khẩu xác nhận không khớp!");
        }

        // 3. Cập nhật mật khẩu mới (đã mã hóa)
        user.setPasswordHash(passwordEncoder.encode(dto.getNewPassword()));
        user.setUpdatedAt(LocalDateTime.now());

        userRepository.save(user);
    }
}
