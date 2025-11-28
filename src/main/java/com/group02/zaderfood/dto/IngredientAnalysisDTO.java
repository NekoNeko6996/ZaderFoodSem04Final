package com.group02.zaderfood.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class IngredientAnalysisDTO {
    private BigDecimal calories;
    private BigDecimal protein;
    private BigDecimal carbs;
    private BigDecimal fat;
}