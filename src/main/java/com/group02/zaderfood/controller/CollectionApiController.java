package com.group02.zaderfood.controller;

import com.group02.zaderfood.entity.Recipe;
import com.group02.zaderfood.repository.CollectionItemRepository;
import com.group02.zaderfood.repository.RecipeRepository; // Giả sử bạn đã có
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/collections")
public class CollectionApiController {

    @Autowired
    private CollectionItemRepository collectionItemRepo;
    
    @Autowired
    private RecipeRepository recipeRepo;

    @GetMapping("/{collectionId}/recipes")
    public ResponseEntity<?> getRecipesByCollection(@PathVariable Integer collectionId) {
        // 1. Lấy tất cả Item trong Collection
        List<Integer> recipeIds = collectionItemRepo.findRecipeIdsByCollectionId(collectionId);
        
        // 2. Lấy thông tin chi tiết Recipe
        List<Recipe> recipes = recipeRepo.findAllById(recipeIds);

        // 3. Chuyển sang DTO nhỏ gọn để trả về JSON
        List<Map<String, Object>> result = recipes.stream().map(r -> {
            // Dùng HashMap thay vì Map.of để tránh lỗi Type Inference
            Map<String, Object> map = new java.util.HashMap<>();
            map.put("recipeId", r.getRecipeId());
            map.put("name", r.getName());
            map.put("calories", r.getTotalCalories());
            map.put("image", r.getImageUrl() != null ? r.getImageUrl() : "/images/default-food.png");
            return map;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }   
}