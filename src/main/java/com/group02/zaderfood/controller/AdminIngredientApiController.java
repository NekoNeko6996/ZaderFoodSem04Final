package com.group02.zaderfood.controller;

import com.group02.zaderfood.dto.IngredientAnalysisDTO;
import com.group02.zaderfood.service.OllamaIngredientService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/ingredients")
public class AdminIngredientApiController {

    @Autowired
    private OllamaIngredientService ollamaService;

    @PostMapping("/analyze")
    public ResponseEntity<?> analyzeIngredient(
            @RequestParam("name") String name,
            @RequestParam("category") String category,
            @RequestParam("baseUnit") String baseUnit,
            @RequestParam(value = "image", required = false) MultipartFile image) {

        // Gọi service Ollama
        IngredientAnalysisDTO result = ollamaService.analyzeIngredient(name, category, baseUnit, image);

        if (result != null) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.badRequest().body("AI could not analyze this ingredient. Please try again or fill manually.");
        }
    }
}