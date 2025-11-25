package com.group02.zaderfood.dto;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class RecipeCreationDTO {
    // Thông tin Recipe
    private String name;
    private String description;
    private String difficulty; // EASY, MEDIUM...
    private Integer prepTimeMin;
    private Integer cookTimeMin;
    private Integer servings;
    private String imageUrl; // URL ảnh sau khi upload

    // Danh sách nguyên liệu (hứng cả cũ và mới)
    private List<IngredientInputDTO> ingredients = new ArrayList<>();

    // Danh sách các bước
    private List<String> steps = new ArrayList<>();
}