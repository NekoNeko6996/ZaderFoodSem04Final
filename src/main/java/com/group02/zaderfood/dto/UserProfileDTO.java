package com.group02.zaderfood.dto;

import com.group02.zaderfood.entity.enums.ActivityLevel;
import com.group02.zaderfood.entity.enums.DietType;
import com.group02.zaderfood.entity.enums.Gender;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;

@Data
public class UserProfileDTO {
    private String fullName;
    private String email;

    //  UserProfiles
    private BigDecimal weightKg;
    private BigDecimal heightCm;    
    private BigDecimal bmr;
    private BigDecimal tdee;
    
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate birthDate;
    private Gender gender;
    private ActivityLevel activityLevel;
    private Integer calorieGoalPerDay;
    private List<DietType> dietaryPreferences;
    private String allergies;
}