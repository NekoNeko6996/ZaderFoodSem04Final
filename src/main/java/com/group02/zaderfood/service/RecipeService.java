package com.group02.zaderfood.service;

// File: service/RecipeService.java
import com.group02.zaderfood.dto.IngredientInputDTO;
import com.group02.zaderfood.dto.RecipeCreationDTO;
import com.group02.zaderfood.entity.Ingredient;
import com.group02.zaderfood.entity.Recipe;
import com.group02.zaderfood.entity.RecipeIngredient;
import com.group02.zaderfood.entity.RecipeStep;
import com.group02.zaderfood.entity.enums.DifficultyLevel;
import com.group02.zaderfood.entity.enums.RecipeStatus;
import com.group02.zaderfood.repository.IngredientRepository;
import com.group02.zaderfood.repository.RecipeIngredientRepository;
import com.group02.zaderfood.repository.RecipeRepository;
import com.group02.zaderfood.repository.RecipeStepRepository;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class RecipeService {

    @Autowired private RecipeRepository recipeRepository;
    @Autowired private IngredientRepository ingredientRepository;
    @Autowired private RecipeIngredientRepository recipeIngredientRepository;
    @Autowired private RecipeStepRepository recipeStepRepository;
    @Autowired private FileStorageService fileStorageService;

    @Transactional // Đảm bảo lưu tất cả hoặc không lưu gì cả nếu lỗi
    public void createFullRecipe(RecipeCreationDTO form, int userId) {
        
        // 1. RECIPES 
        Recipe recipe = new Recipe();
        recipe.setName(form.getName());
        recipe.setDescription(form.getDescription());
        recipe.setDifficulty(DifficultyLevel.valueOf(form.getDifficulty())); // Convert String to Enum
        recipe.setPrepTimeMin(form.getPrepTimeMin());
        recipe.setCookTimeMin(form.getCookTimeMin());
        recipe.setServings(form.getServings());
        recipe.setCreatedByUserId(userId);
        recipe.setStatus(RecipeStatus.PENDING);
        recipe.setCreatedAt(LocalDateTime.now());
        recipe.setUpdatedAt(LocalDateTime.now());
        
        // process file
        String coverImgUrl = fileStorageService.storeFile(form.getImageFile());
        String videoUrl = fileStorageService.storeFile(form.getVideoFile());
        
        recipe.setImageUrl(coverImgUrl);
        
        // Lưu Recipe để lấy ID
        Recipe savedRecipe = recipeRepository.save(recipe);
        

        // 2. LƯU NGUYÊN LIỆU (Xử lý tách Mới/Cũ) [cite: 18, 145]
        for (IngredientInputDTO input : form.getIngredients()) {
            Integer finalIngredientId = null;

            if (Boolean.TRUE.equals(input.getIsNewIngredient())) {
                // --- TRƯỜNG HỢP A: NGUYÊN LIỆU MỚI (User tự nhập) ---
                Ingredient newIng = new Ingredient();
                newIng.setName(input.getNewName());
                newIng.setCaloriesPer100g(input.getCaloriesPer100g());
                newIng.setProtein(input.getProtein());
                newIng.setFat(input.getFat());
                newIng.setCarbs(input.getCarbs());
                newIng.setCategoryId(input.getCategoryId());
                newIng.setCreatedAt(LocalDateTime.now());
                newIng.setUpdatedAt(LocalDateTime.now());
                
                String ingImgUrl = fileStorageService.storeFile(input.getNewIngredientImage());
                newIng.setImageUrl(ingImgUrl);
                
                // Quan trọng: Đánh dấu là chưa duyệt & lưu người tạo
                newIng.setIsActive(false); // Hoặc set Status = PENDING
                newIng.setCreatedByUserId(userId); 

                // Lưu nguyên liệu mới xuống DB
                Ingredient savedIng = ingredientRepository.save(newIng);
                finalIngredientId = savedIng.getIngredientId();
            } else {
                // --- TRƯỜNG HỢP B: NGUYÊN LIỆU CÓ SẴN ---
                finalIngredientId = input.getExistingIngredientId();
            }

            // 3. TẠO LIÊN KẾT (Recipe - Ingredient) vào bảng RecipeIngredients [cite: 5]
            if (finalIngredientId != null) {
                RecipeIngredient link = new RecipeIngredient();
                link.setRecipeId(savedRecipe.getRecipeId());
                link.setIngredientId(finalIngredientId);
                link.setQuantity(input.getQuantity());
                link.setUnit(input.getUnit());
                link.setNote(input.getNote());
                
                recipeIngredientRepository.save(link);
            }
        }

        // 4. LƯU CÁC BƯỚC NẤU (STEPS) [cite: 5]
        // Lưu ý: Trong DTO form.steps nên là List<String> instruction
        if (form.getSteps() != null) {
            int stepNum = 1;
            for (String instruction : form.getSteps()) {
                if (instruction != null && !instruction.trim().isEmpty()) {
                    RecipeStep step = new RecipeStep();
                    step.setRecipeId(savedRecipe.getRecipeId());
                    step.setStepNumber(stepNum++);
                    step.setInstruction(instruction);
                    // step.setMediaUrl(...) // Nếu có upload ảnh bước
                    step.setCreatedAt(LocalDateTime.now());
                    step.setUpdatedAt(LocalDateTime.now());
                    
                    recipeStepRepository.save(step);
                }
            }
        }
    }
}