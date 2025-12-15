package com.group02.zaderfood.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Builder
public class UnifiedRecipeDTO {
    private Integer id;          // RecipeId hoặc AiRecipeId
    private String name;
    private String imageUrl;
    private BigDecimal calories;
    private Integer timeMin;     // Prep + Cook
    private boolean isAi;        // Cờ đánh dấu: true = AI, false = Standard
    private String difficulty;   // Standard có Easy/Hard, AI sẽ ghi là "AI Generated"
}