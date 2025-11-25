package com.group02.zaderfood.dto;


import lombok.Data;
import java.math.BigDecimal;

@Data
public class IngredientInputDTO {
    // --- Phần chung cho công thức ---
    private BigDecimal quantity;
    private String unit;
    private String note;

    // --- Logic phân loại ---
    private Boolean isNewIngredient = false; // Checkbox: true = nhập mới, false = chọn cũ

    // --- Trường hợp 1: Chọn từ kho có sẵn ---
    private Integer existingIngredientId;

    // --- Trường hợp 2: Nhập mới hoàn toàn ---
    private String newName;
    private BigDecimal caloriesPer100g;
    private BigDecimal protein;
    private BigDecimal carbs;
    private BigDecimal fat;
    private Integer categoryId; // Chọn loại (Thịt, Rau...)
}