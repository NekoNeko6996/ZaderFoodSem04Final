package com.group02.zaderfood.entity;

import com.group02.zaderfood.entity.enums.ActivityLevel;
import com.group02.zaderfood.entity.enums.DietType;
import com.group02.zaderfood.entity.enums.Gender;
import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "UserProfiles")
public class UserProfile implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "UserId")
    private Integer userId;

    @Column(name = "WeightKg")
    private BigDecimal weightKg;

    @Column(name = "HeightCm")
    private BigDecimal heightCm;

    @Column(name = "BirthDate")
    private LocalDate birthDate;

    @Column(name = "Gender")
    @Enumerated(EnumType.STRING)
    private Gender gender;

    @Column(name = "ActivityLevel")
    @Enumerated(EnumType.STRING)
    private ActivityLevel activityLevel;

    @Column(name = "CalorieGoalPerDay")
    private Integer calorieGoalPerDay;

    @Column(name = "DietaryPreference")
    @Enumerated(EnumType.STRING)
    private DietType dietaryPreference;

    @Column(name = "Allergies")
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
