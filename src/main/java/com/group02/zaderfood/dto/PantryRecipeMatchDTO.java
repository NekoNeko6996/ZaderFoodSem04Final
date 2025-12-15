package com.group02.zaderfood.dto;

import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PantryRecipeMatchDTO {
    // Các trường cũ (giữ nguyên để tương thích code cũ)
    private Integer recipeId; // Có thể null nếu là món mới từ AI
    private String name;
    private String imageUrl;
    private Integer timeMin; 
    private BigDecimal calories;
    private int matchPercentage; 
    private int missingCount;

    // [NEW] CÁC TRƯỜNG BỔ SUNG CHO AI
    private String description;
    private Integer servings;
    private List<String> ingredientsList; // VD: ["2 eggs", "100ml milk"]
    private List<String> stepsList;       // VD: ["Mix eggs", "Fry pan"]
}