package com.group02.zaderfood.dto;

import com.group02.zaderfood.entity.enums.ActivityLevel;
import com.group02.zaderfood.entity.enums.Gender;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class NutritionCalculatorDTO {
    // Input
    private Integer age;
    private BigDecimal weightKg;
    private BigDecimal heightCm;
    private Gender gender;
    private ActivityLevel activityLevel;
    private String goal; // LOSE, MAINTAIN, GAIN

    // Output (Kết quả tính toán)
    private BigDecimal bmr;  // Năng lượng nền
    private BigDecimal tdee; // Năng lượng tiêu thụ mỗi ngày
    private Integer dailyCalorieTarget; // Calo mục tiêu dựa trên Goal

    // Macros (Gram)
    private Integer proteinGrams;
    private Integer carbsGrams;
    private Integer fatGrams;
}