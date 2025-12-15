package com.group02.zaderfood.controller;

import com.group02.zaderfood.dto.PantryRecipeMatchDTO;
import com.group02.zaderfood.entity.AiSavedRecipes;
import com.group02.zaderfood.entity.CollectionItem;
import com.group02.zaderfood.entity.RecipeCollection;
import com.group02.zaderfood.entity.UserPantry;
import com.group02.zaderfood.repository.AiSavedRecipeRepository;
import com.group02.zaderfood.repository.CollectionItemRepository;
import com.group02.zaderfood.repository.IngredientRepository;
import com.group02.zaderfood.repository.RecipeCollectionRepository;
import com.group02.zaderfood.service.CustomUserDetails;
import com.group02.zaderfood.service.PantryService;
import jakarta.servlet.http.HttpSession; // Import Session
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/user/pantry")
public class PantryController {

    @Autowired
    private PantryService pantryService;
    @Autowired
    private IngredientRepository ingredientRepository;
    @Autowired
    private AiSavedRecipeRepository aiSavedRepo;

    @Autowired
    private RecipeCollectionRepository collectionRepo;
    @Autowired
    private CollectionItemRepository collectionItemRepo;

    @GetMapping
    public String viewPantry(Model model, Authentication authentication, HttpSession session) {
        CustomUserDetails user = (CustomUserDetails) authentication.getPrincipal();
        Integer userId = user.getUser().getUserId();

        // 1. Lấy dữ liệu Pantry (Cũ)
        List<UserPantry> pantryItems = pantryService.getUserPantry(userId);
        model.addAttribute("pantryItems", pantryItems);
        model.addAttribute("allIngredients", ingredientRepository.findByIsActiveTrue());

        // 2. [NEW] Kiểm tra Session xem có AI Suggestions không
        List<PantryRecipeMatchDTO> aiSuggestions = (List<PantryRecipeMatchDTO>) session.getAttribute("AI_SUGGESTIONS");

        if (aiSuggestions != null && !aiSuggestions.isEmpty()) {
            model.addAttribute("aiSuggestions", aiSuggestions);
            model.addAttribute("hasAiSuggestions", true);
        } else {
            model.addAttribute("hasAiSuggestions", false);
        }

        return "user/pantry";
    }

    @PostMapping("/add")
    public String addItem(@RequestParam Integer ingredientId,
            @RequestParam BigDecimal quantity,
            @RequestParam String unit,
            @RequestParam LocalDate expiryDate,
            Authentication authentication) {
        CustomUserDetails user = (CustomUserDetails) authentication.getPrincipal();
        pantryService.addToPantry(user.getUser().getUserId(), ingredientId, quantity, unit, expiryDate);
        return "redirect:/user/pantry";
    }

    @PostMapping("/delete/{id}")
    public String deleteItem(@PathVariable Integer id) {
        pantryService.removeFromPantry(id);
        return "redirect:/user/pantry";
    }

    @GetMapping("/suggest")
    @ResponseBody
    public ResponseEntity<?> getSmartSuggestions(Authentication authentication) {
        try {
            CustomUserDetails user = (CustomUserDetails) authentication.getPrincipal();
            List<PantryRecipeMatchDTO> suggestions = pantryService.suggestRecipes(user.getUser().getUserId());
            if (suggestions.size() > 3) {
                suggestions = suggestions.subList(0, 3);
            }
            return ResponseEntity.ok(Map.of("success", true, "data", suggestions));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // --- CẬP NHẬT: AI SUGGESTION VỚI SESSION ---
    @GetMapping("/suggest-ai")
    @ResponseBody
    public ResponseEntity<?> getAiSuggestions(Authentication authentication, HttpSession session) {
        try {
            CustomUserDetails user = (CustomUserDetails) authentication.getPrincipal();
            List<PantryRecipeMatchDTO> suggestions = pantryService.suggestAiRecipes(user.getUser().getUserId());

            // [QUAN TRỌNG] Lưu kết quả vào Session để dùng cho trang chi tiết
            session.setAttribute("AI_SUGGESTIONS", suggestions);

            return ResponseEntity.ok(Map.of("success", true, "data", suggestions));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/ai-recipe/{index}")
    public String viewAiRecipeDetail(@PathVariable int index, HttpSession session, Model model) {
        List<PantryRecipeMatchDTO> suggestions = (List<PantryRecipeMatchDTO>) session.getAttribute("AI_SUGGESTIONS");

        if (suggestions == null || index < 0 || index >= suggestions.size()) {
            return "redirect:/user/pantry";
        }

        model.addAttribute("recipe", suggestions.get(index));
        model.addAttribute("recipeIndex", index);

        // [CONFIG NÚT BACK] Về Pantry
        model.addAttribute("backUrl", "/user/pantry");
        model.addAttribute("backLabel", "Back to Pantry");
        model.addAttribute("isSavedView", false); // Hiện nút Save

        return "user/aiRecipeDetail";
    }

    // API Lưu món từ Session vào DB
    @PostMapping("/save-ai-recipe")
    @ResponseBody
    public ResponseEntity<?> saveAiRecipe(@RequestBody PantryRecipeMatchDTO recipeDto, Authentication auth) {
        try {
            CustomUserDetails user = (CustomUserDetails) auth.getPrincipal();
            Integer userId = user.getUser().getUserId();

            // 1. LƯU MÓN ĂN VÀO BẢNG AI_SAVED_RECIPES
            AiSavedRecipes entity = new AiSavedRecipes();
            entity.setUserId(userId);
            entity.setName(recipeDto.getName());
            entity.setDescription(recipeDto.getDescription());
            entity.setTimeMinutes(recipeDto.getTimeMin());
            entity.setTotalCalories(recipeDto.getCalories());
            entity.setServings(recipeDto.getServings());
            entity.setImageUrl(recipeDto.getImageUrl());
            entity.setIngredientsText(String.join("\n", recipeDto.getIngredientsList()));
            entity.setStepsText(String.join("\n", recipeDto.getStepsList()));
            entity.setSavedAt(LocalDateTime.now());

            AiSavedRecipes savedRecipe = aiSavedRepo.save(entity);

            // 2. TỰ ĐỘNG TẠO/TÌM COLLECTION "AI Chef Favorites"
            String collectionName = "AI Chef Favorites";
            RecipeCollection aiCollection = collectionRepo.findByUserIdAndName(userId, collectionName)
                    .orElseGet(() -> {
                        // Nếu chưa có thì tạo mới
                        RecipeCollection newCol = new RecipeCollection();
                        newCol.setUserId(userId);
                        newCol.setName(collectionName);
                        newCol.setIsPublic(false);
                        newCol.setIsDeleted(false);
                        newCol.setCreatedAt(LocalDateTime.now());
                        return collectionRepo.save(newCol);
                    });

            // 3. LINK VÀO COLLECTION ITEM
            CollectionItem linkItem = new CollectionItem();
            linkItem.setCollectionId(aiCollection.getCollectionId());
            linkItem.setAiRecipeId(savedRecipe.getAiRecipeId()); // Link tới món AI
            linkItem.setRecipeId(null); // Món này không phải Recipe thường
            linkItem.setAddedAt(LocalDateTime.now());

            collectionItemRepo.save(linkItem);

            return ResponseEntity.ok(Map.of("success", true, "message", "Saved to 'AI Chef Favorites' collection!"));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Error: " + e.getMessage()));
        }
    }

    // API Xóa Session (Clear Button)
    @PostMapping("/clear-ai-session")
    @ResponseBody
    public ResponseEntity<?> clearAiSession(HttpSession session) {
        session.removeAttribute("AI_SUGGESTIONS");
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/ai-recipe/{index}/save")
    public String saveAiRecipeFromSession(@PathVariable int index, HttpSession session, @AuthenticationPrincipal CustomUserDetails user) {
        List<PantryRecipeMatchDTO> suggestions = (List<PantryRecipeMatchDTO>) session.getAttribute("AI_SUGGESTIONS");

        if (suggestions != null && index >= 0 && index < suggestions.size()) {
            PantryRecipeMatchDTO dto = suggestions.get(index);
            Integer userId = user.getUser().getUserId();

            // 1. Lưu AI Recipe
            AiSavedRecipes entity = new AiSavedRecipes();
            entity.setUserId(userId);
            entity.setName(dto.getName());
            entity.setDescription(dto.getDescription());
            entity.setTimeMinutes(dto.getTimeMin());
            entity.setTotalCalories(dto.getCalories());
            entity.setServings(dto.getServings());
            entity.setImageUrl(dto.getImageUrl());
            entity.setIngredientsText(String.join("\n", dto.getIngredientsList()));
            entity.setStepsText(String.join("\n", dto.getStepsList()));
            entity.setSavedAt(LocalDateTime.now());

            AiSavedRecipes savedRecipe = aiSavedRepo.save(entity);

            // 2. Tìm/Tạo Collection
            RecipeCollection aiCollection = collectionRepo.findByUserIdAndName(userId, "AI Chef Favorites")
                    .orElseGet(() -> {
                        RecipeCollection newCol = new RecipeCollection();
                        newCol.setUserId(userId);
                        newCol.setName("AI Chef Favorites");
                        newCol.setIsPublic(false);
                        newCol.setIsDeleted(false);
                        newCol.setCreatedAt(LocalDateTime.now());
                        return collectionRepo.save(newCol);
                    });

            // 3. Link Item
            CollectionItem linkItem = new CollectionItem();
            linkItem.setCollectionId(aiCollection.getCollectionId());
            linkItem.setAiRecipeId(savedRecipe.getAiRecipeId());
            linkItem.setAddedAt(LocalDateTime.now());
            collectionItemRepo.save(linkItem);
        }

        return "redirect:/user/pantry?success=saved";
    }
}
