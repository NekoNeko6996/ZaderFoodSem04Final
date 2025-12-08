package com.group02.zaderfood.service;

import com.group02.zaderfood.dto.ChangePasswordDTO;
import com.group02.zaderfood.dto.NutritionCalculatorDTO;
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
import com.group02.zaderfood.entity.enums.Gender;
import com.group02.zaderfood.repository.UserDietaryPreferenceRepository;
import com.group02.zaderfood.repository.UserProfileRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
        dto.setGoal(profile.getGoal());

        dto.setBmr(profile.getBmr());
        dto.setTdee(profile.getTdee());

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
        profile.setGoal(dto.getGoal());

        recalculateBodyMetrics(profile, dto.getDietaryPreferences());

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

    /**
     * Hàm tính toán chỉ số cơ thể tự động (Smart Calculator)
     *
     * @param profile Thông tin user (Cân nặng, chiều cao, tuổi...)
     * @param diets Danh sách chế độ ăn user chọn (để override tỷ lệ Macros)
     */
    private void recalculateBodyMetrics(UserProfile profile, List<DietType> diets) {
        // 1. VALIDATION: Kiểm tra dữ liệu đầu vào
        if (profile.getWeightKg() == null || profile.getHeightCm() == null
                || profile.getBirthDate() == null || profile.getGender() == null
                || profile.getActivityLevel() == null) {
            return; // Thiếu dữ liệu thì không tính, giữ nguyên giá trị cũ hoặc null
        }

        // 2. CHUẨN BỊ DỮ LIỆU SỐ
        double weight = profile.getWeightKg().doubleValue();
        double height = profile.getHeightCm().doubleValue();
        int age = java.time.Period.between(profile.getBirthDate(), java.time.LocalDate.now()).getYears();

        // 3. TÍNH BMR (Basal Metabolic Rate) - Công thức Mifflin-St Jeor
        double bmrValue;
        if (profile.getGender() == Gender.MALE) {
            bmrValue = (10 * weight) + (6.25 * height) - (5 * age) + 5;
        } else {
            bmrValue = (10 * weight) + (6.25 * height) - (5 * age) - 161;
        }
        profile.setBmr(BigDecimal.valueOf(bmrValue).setScale(0, RoundingMode.HALF_UP));

        // 4. TÍNH TDEE (Total Daily Energy Expenditure)
        double multiplier = 1.2;
        switch (profile.getActivityLevel()) {
            case SEDENTARY:
                multiplier = 1.2;
                break;        // Ít vận động
            case LIGHTLY_ACTIVE:
                multiplier = 1.375;
                break; // 1-3 ngày/tuần
            case MODERATELY_ACTIVE:
                multiplier = 1.55;
                break; // 3-5 ngày/tuần
            case VERY_ACTIVE:
                multiplier = 1.725;
                break;    // 6-7 ngày/tuần
            case EXTRA_ACTIVE:
                multiplier = 1.9;
                break;     // VĐV chuyên nghiệp
        }
        double tdeeValue = bmrValue * multiplier;
        profile.setTdee(BigDecimal.valueOf(tdeeValue).setScale(0, RoundingMode.HALF_UP));

        // 5. TÍNH MỤC TIÊU CALO (CALORIE TARGET) - Dựa trên Goal
        double targetCal = tdeeValue;

        if (profile.getGoal() != null) {
            switch (profile.getGoal()) {
                case WEIGHT_LOSS:
                    targetCal -= 500; // Thâm hụt để giảm cân (~0.5kg/tuần)
                    break;
                case MUSCLE_GAIN:
                    targetCal += 300; // Dư calo vừa phải để nuôi cơ (Lean Bulk)
                    break;
                case WEIGHT_GAIN:
                    targetCal += 500; // Dư nhiều calo để tăng cân nhanh (Dirty Bulk)
                    break;
                case MAINTENANCE:
                default:
                    // Giữ nguyên TDEE
                    break;
            }
        }

        // Safety Check: Không để Calo mục tiêu thấp hơn BMR (tránh suy nhược cơ thể)
        if (targetCal < bmrValue) {
            targetCal = bmrValue;
        }

        int finalCal = (int) targetCal;
        profile.setCalorieGoalPerDay(finalCal);

        // 6. TÍNH TỶ LỆ MACROS (PROTEIN - CARBS - FAT)
        // Mặc định (Maintenance/Balanced): 25% Pro - 50% Carb - 25% Fat
        double ratioProt = 0.25;
        double ratioCarb = 0.50;
        double ratioFat = 0.25;

        boolean isDietOverridden = false;

        // A. ƯU TIÊN 1: XÉT DIETARY PREFERENCES (Chế độ ăn đặc thù)
        // Nếu user chọn chế độ ăn đặc biệt, tỷ lệ này sẽ đè lên tỷ lệ của Goal
        if (diets != null && !diets.isEmpty()) {
            if (diets.contains(DietType.KETO)) {
                // Keto: Fat cực cao, Carb cực thấp
                ratioCarb = 0.05; // 5%
                ratioProt = 0.25; // 25%
                ratioFat = 0.70;  // 70%
                isDietOverridden = true;
            } else if (diets.contains(DietType.LOW_CARB)) {
                // Low Carb: Giảm Carb vừa phải, tăng Protein & Fat
                ratioCarb = 0.20; // 20%
                ratioProt = 0.40; // 40%
                ratioFat = 0.40;  // 40%
                isDietOverridden = true;
            } else if (diets.contains(DietType.HIGH_PROTEIN)) {
                // High Protein: Ưu tiên Protein
                ratioProt = 0.45; // 45%
                ratioCarb = 0.30; // 30%
                ratioFat = 0.25;  // 25%
                isDietOverridden = true;
            }
            // Các diet khác (Vegan, Dairy Free...) thường không ép buộc tỷ lệ Macro, 
            // nên để nó rơi xuống logic theo Goal bên dưới.
        }

        // B. ƯU TIÊN 2: XÉT GOAL (Nếu không bị Diet override)
        if (!isDietOverridden && profile.getGoal() != null) {
            switch (profile.getGoal()) {
                case WEIGHT_LOSS:
                    // Giảm cân: Cần Protein cao để giữ cơ, giảm Carb/Fat
                    ratioProt = 0.40;
                    ratioCarb = 0.30;
                    ratioFat = 0.30;
                    break;

                case MUSCLE_GAIN:
                    // Tăng cơ: Cần Protein xây cơ + Carb để tập nặng, Fat vừa phải
                    ratioProt = 0.35;
                    ratioCarb = 0.45;
                    ratioFat = 0.20;
                    break;

                case WEIGHT_GAIN:
                    // Tăng cân: Tăng Fat (nhiều năng lượng), Carb & Pro cân bằng
                    ratioProt = 0.30;
                    ratioCarb = 0.35;
                    ratioFat = 0.35;
                    break;

                case MAINTENANCE:
                default:
                    // Cân bằng (25-50-25)
                    ratioProt = 0.25;
                    ratioCarb = 0.50;
                    ratioFat = 0.25;
                    break;
            }
        }

        // 7. QUY ĐỔI RA GAM (Grams) VÀ LƯU VÀO PROFILE
        // 1g Protein = 4 kcal
        // 1g Carbs   = 4 kcal
        // 1g Fat     = 9 kcal
        profile.setProteinGoal((int) ((finalCal * ratioProt) / 4));
        profile.setCarbsGoal((int) ((finalCal * ratioCarb) / 4));
        profile.setFatGoal((int) ((finalCal * ratioFat) / 9));
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

    public NutritionCalculatorDTO calculateNutrition(NutritionCalculatorDTO dto) {
        double weight = dto.getWeightKg().doubleValue();
        double height = dto.getHeightCm().doubleValue();
        int age = dto.getAge();
        double bmrValue = 0;

        // Tính BMR
        if (dto.getGender() == Gender.MALE) {
            bmrValue = (10 * weight) + (6.25 * height) - (5 * age) + 5;
        } else {
            bmrValue = (10 * weight) + (6.25 * height) - (5 * age) - 161;
        }

        // Tính TDEE dựa trên Activity Level
        double multiplier = 1.2;
        switch (dto.getActivityLevel()) {
            case SEDENTARY:
                multiplier = 1.2;
                break;
            case LIGHTLY_ACTIVE:
                multiplier = 1.375;
                break;
            case MODERATELY_ACTIVE:
                multiplier = 1.55;
                break;
            case VERY_ACTIVE:
                multiplier = 1.725;
                break;
            case EXTRA_ACTIVE:
                multiplier = 1.9;
                break;
        }
        double tdeeValue = bmrValue * multiplier;

        // Tính Calorie Target dựa trên Goal
        double targetCalories = tdeeValue;
        if ("LOSE".equals(dto.getGoal())) {
            targetCalories -= 500; // Giảm cân: thâm hụt 500 calo
        } else if ("GAIN".equals(dto.getGoal())) {
            targetCalories += 500; // Tăng cơ: dư 500 calo
        }

        // Set kết quả vào DTO
        dto.setBmr(BigDecimal.valueOf(bmrValue).setScale(0, RoundingMode.HALF_UP));
        dto.setTdee(BigDecimal.valueOf(tdeeValue).setScale(0, RoundingMode.HALF_UP));
        dto.setDailyCalorieTarget((int) targetCalories);

        // Tính Macros (Tỷ lệ tham khảo: 30% Protein, 45% Carbs, 25% Fat)
        // 1g Protein = 4 calo, 1g Carbs = 4 calo, 1g Fat = 9 calo
        dto.setProteinGrams((int) ((targetCalories * 0.30) / 4));
        dto.setCarbsGrams((int) ((targetCalories * 0.45) / 4));
        dto.setFatGrams((int) ((targetCalories * 0.25) / 9));

        return dto;
    }

    // 2. Lưu kết quả vào Profile User
    @Transactional
    public void saveNutritionProfile(Integer userId, NutritionCalculatorDTO resultDto) throws Exception {
        UserProfile profile = userProfileRepository.findByUserId(userId)
                .orElse(new UserProfile());

        if (profile.getUserId() == null) {
            profile.setUserId(userId);
            profile.setCreatedAt(LocalDateTime.now());
        }

        // Cập nhật các chỉ số vật lý nếu user nhập mới
        profile.setWeightKg(resultDto.getWeightKg());
        profile.setHeightCm(resultDto.getHeightCm());
        profile.setGender(resultDto.getGender());
        profile.setActivityLevel(resultDto.getActivityLevel());

        // Cập nhật chỉ số dinh dưỡng [QUAN TRỌNG]
        profile.setBmr(resultDto.getBmr());
        profile.setTdee(resultDto.getTdee());
        profile.setCalorieGoalPerDay(resultDto.getDailyCalorieTarget());

        profile.setUpdatedAt(LocalDateTime.now());
        userProfileRepository.save(profile);
    }
}
