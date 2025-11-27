package com.group02.zaderfood.dto;

import com.group02.zaderfood.entity.Recipe;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RecipeMatchDTO {
    private Recipe recipe;
    private int matchPercentage;          // EX: 80%
    private int missingCount;             
    private List<String> missingIngredients;
}