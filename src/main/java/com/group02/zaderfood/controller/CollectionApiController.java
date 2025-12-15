package com.group02.zaderfood.controller;

import com.group02.zaderfood.entity.AiSavedRecipes;
import com.group02.zaderfood.entity.CollectionItem;
import com.group02.zaderfood.entity.Recipe;
import com.group02.zaderfood.repository.AiSavedRecipeRepository;
import com.group02.zaderfood.repository.CollectionItemRepository;
import com.group02.zaderfood.repository.RecipeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/collections")
public class CollectionApiController {

    @Autowired
    private CollectionItemRepository collectionItemRepo;

    @Autowired
    private RecipeRepository recipeRepo;

    @Autowired
    private AiSavedRecipeRepository aiSavedRepo; // [NEW] Inject thêm Repo AI

    @GetMapping("/{collectionId}/recipes")
    public ResponseEntity<?> getRecipesByCollection(@PathVariable Integer collectionId) {
        // 1. Lấy tất cả Item trong Collection
        List<CollectionItem> items = collectionItemRepo.findByCollectionId(collectionId);

        // 2. Tách danh sách ID
        List<Integer> standardIds = items.stream()
                .map(CollectionItem::getRecipeId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        List<Integer> aiIds = items.stream()
                .map(CollectionItem::getAiRecipeId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        // 3. Query DB lấy dữ liệu chi tiết
        List<Recipe> standardRecipes = standardIds.isEmpty() ? new ArrayList<>() : recipeRepo.findAllById(standardIds);
        List<AiSavedRecipes> aiRecipes = aiIds.isEmpty() ? new ArrayList<>() : aiSavedRepo.findAllById(aiIds);

        // 4. Merge kết quả vào 1 List Map chung
        List<Map<String, Object>> result = new ArrayList<>();

        // 4a. Map món thường
        for (Recipe r : standardRecipes) {
            Map<String, Object> map = new HashMap<>();
            map.put("recipeId", r.getRecipeId());
            map.put("name", r.getName());
            map.put("calories", r.getTotalCalories());
            map.put("image", r.getImageUrl() != null ? r.getImageUrl() : "/images/default-food.png");
            map.put("isAi", false); // Cờ đánh dấu để Frontend biết đường dẫn link
            map.put("link", "/recipes/detail/" + r.getRecipeId());
            result.add(map);
        }

        // 4b. Map món AI
        for (AiSavedRecipes ar : aiRecipes) {
            Map<String, Object> map = new HashMap<>();
            map.put("recipeId", ar.getAiRecipeId()); // ID của bảng AI
            map.put("name", ar.getName());
            map.put("calories", ar.getTotalCalories());
            map.put("image", ar.getImageUrl() != null ? ar.getImageUrl() : "https://placehold.co/300?text=AI+Dish");
            map.put("isAi", true); // Cờ đánh dấu là AI
            // Link này phải trỏ đến trang chi tiết món đã lưu (bạn cần tạo route này nếu chưa có, ví dụ /user/pantry/saved/...)
            // Hoặc tạm thời dùng modal
            map.put("link", "#");
            result.add(map);
        }

        return ResponseEntity.ok(result);
    }
}
