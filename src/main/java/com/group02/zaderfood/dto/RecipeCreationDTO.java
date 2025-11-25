package com.group02.zaderfood.dto;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

@Data
public class RecipeCreationDTO {
    private String name;
    private String description;
    private String difficulty;
    private Integer prepTimeMin;
    private Integer cookTimeMin;
    private Integer servings;
    private MultipartFile imageFile; 
    private MultipartFile videoFile;

    private List<IngredientInputDTO> ingredients = new ArrayList<>();

    private List<String> steps = new ArrayList<>();
}