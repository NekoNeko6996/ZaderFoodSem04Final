package com.group02.zaderfood.entity;

import com.group02.zaderfood.entity.enums.ActivityLevel;
import com.group02.zaderfood.entity.enums.Gender;
import com.group02.zaderfood.entity.enums.UserGoal;
import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.*;
import org.hibernate.annotations.Nationalized;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "UserProfiles")
public class UserProfile implements Serializable {

    @Id
    @Column(name = "UserId")
    private Integer userId;

    @Column(name = "WeightKg")
    private BigDecimal weightKg;

    @Column(name = "HeightCm")
    private BigDecimal heightCm;

    @Column(name = "BirthDate")
    private LocalDate birthDate;

    @Column(name = "Gender")
    @Nationalized
    @Enumerated(EnumType.STRING)
    private Gender gender;

    @Column(name = "ActivityLevel")
    @Nationalized
    @Enumerated(EnumType.STRING)
    private ActivityLevel activityLevel;

    // [NEW] Thêm chỉ số BMR từ DB v2.0
    @Column(name = "BMR")
    private BigDecimal bmr;

    // [NEW] Thêm chỉ số TDEE từ DB v2.0
    @Column(name = "TDEE")
    private BigDecimal tdee;

    @Column(name = "CalorieGoalPerDay")
    private Integer calorieGoalPerDay;
    
    @Column(name = "Goal")
    @Enumerated(EnumType.STRING)
    private UserGoal goal;

    @Column(name = "ProteinGoal")
    private Integer proteinGoal;

    @Column(name = "CarbsGoal")
    private Integer carbsGoal;

    @Column(name = "FatGoal")
    private Integer fatGoal;

    // [DELETED] Đã xóa dietaryPreference vì chuyển sang bảng UserDietaryPreferences
    @Column(name = "Allergies")
    @Nationalized
    private String allergies;

    @Column(name = "CreatedAt")
    private LocalDateTime createdAt;

    @Column(name = "UpdatedAt")
    private LocalDateTime updatedAt;

    @Column(name = "IsDeleted")
    private Boolean isDeleted;

    @Column(name = "DeletedAt")
    private LocalDateTime deletedAt;
}
