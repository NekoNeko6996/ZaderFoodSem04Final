package com.group02.zaderfood.controller;

import com.group02.zaderfood.dto.AiFoodResponse;
import com.group02.zaderfood.service.AiFoodService;
import com.group02.zaderfood.service.CustomUserDetails;
import java.util.Map;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping("/ai-tools")
public class AiToolsController {
    
    @Autowired private AiFoodService aiFoodService;

    @GetMapping("/calorie-estimator")
    public String showCalorieEstimatorPage() {
        return "ai/ai-calorie-estimator";
    }

    @PostMapping("/save-recipe")
    @ResponseBody
    public ResponseEntity<?> saveAiRecipe(@RequestBody SaveAiRecipeRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        try {
            if (user == null) {
                return ResponseEntity.status(401).body("Unauthorized");
            }

            // request.getData() chứa JSON từ AI trả về
            // request.getImageUrl() là link ảnh (nếu có)
            aiFoodService.saveAiRecipeToCollection(user.getUserId(), request.getData(), request.getImageUrl());

            return ResponseEntity.ok(Map.of("message", "Recipe saved to Ai Chef Favorites!"));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to save: " + e.getMessage()));
        }
    }

    // DTO hứng dữ liệu từ Body
    @Data
    public static class SaveAiRecipeRequest {

        private AiFoodResponse data;
        private String imageUrl;
    }
}
