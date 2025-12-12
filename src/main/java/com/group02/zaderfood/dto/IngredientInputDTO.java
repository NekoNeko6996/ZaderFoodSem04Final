package com.group02.zaderfood.dto;


import lombok.Data;
import java.math.BigDecimal;
import org.springframework.web.multipart.MultipartFile;

@Data
public class IngredientInputDTO {
    
    private Integer ingredientId; 
    private String name;        
    private MultipartFile imageFile;
    
    private String newName;
    private Integer categoryId;

    private Integer existingIngredientId;
    private Boolean isNewIngredient = false;
    
    private BigDecimal caloriesPer100g;
    private BigDecimal protein;
    private BigDecimal carbs;
    private BigDecimal fat;
    private String baseUnit;
    
    private MultipartFile newIngredientImage;
    
    private BigDecimal quantity;
    private String unit;
    private String note;
}