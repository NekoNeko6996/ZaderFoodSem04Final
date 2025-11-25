package com.group02.zaderfood.dto;


import lombok.Data;
import java.math.BigDecimal;
import org.springframework.web.multipart.MultipartFile;

@Data
public class IngredientInputDTO {
    private BigDecimal quantity;
    private String unit;
    private String note;

    private Boolean isNewIngredient = false;

    private Integer existingIngredientId;

    private String newName;
    private BigDecimal caloriesPer100g;
    private BigDecimal protein;
    private BigDecimal carbs;
    private BigDecimal fat;
    private Integer categoryId;
    private MultipartFile newIngredientImage;
}