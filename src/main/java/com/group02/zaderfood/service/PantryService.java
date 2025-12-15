package com.group02.zaderfood.service;

import com.group02.zaderfood.dto.PantryRecipeMatchDTO;
import com.group02.zaderfood.entity.Recipe;
import com.group02.zaderfood.entity.RecipeIngredient;
import com.group02.zaderfood.entity.UserPantry;
import com.group02.zaderfood.entity.UserProfile;
import com.group02.zaderfood.repository.IngredientRepository;
import com.group02.zaderfood.repository.RecipeIngredientRepository;
import com.group02.zaderfood.repository.RecipeRepository;
import com.group02.zaderfood.repository.UserDietaryPreferenceRepository;
import com.group02.zaderfood.repository.UserPantryRepository;
import com.group02.zaderfood.repository.UserProfileRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PantryService {

    @Autowired
    private UserPantryRepository pantryRepository;
    @Autowired
    private IngredientRepository ingredientRepository; // Cần repo này để lấy thông tin unit mặc định

    @Autowired
    private RecipeRepository recipeRepository;
    @Autowired
    private RecipeIngredientRepository recipeIngredientRepository;

    @Autowired
    private UserProfileRepository userProfileRepository;
    @Autowired
    private UserDietaryPreferenceRepository dietaryRepo;
    @Autowired
    private AiFoodService aiFoodService;

    public List<UserPantry> getUserPantry(Integer userId) {
        return pantryRepository.findByUserIdOrderByExpiryDateAsc(userId);
    }

    @Transactional
    public void addToPantry(Integer userId, Integer ingredientId, BigDecimal qty, String unit, LocalDate expiry) {
        // Kiểm tra xem món này đã có trong tủ chưa
        UserPantry existing = pantryRepository.findByUserIdAndIngredientId(userId, ingredientId);

        if (existing != null) {
            // Nếu có rồi -> Cộng dồn số lượng & Cập nhật hạn sử dụng mới nhất (hoặc logic tùy bạn)
            existing.setQuantity(existing.getQuantity().add(qty));
            existing.setExpiryDate(expiry); // Cập nhật hạn mới
            pantryRepository.save(existing);
        } else {
            // Nếu chưa -> Tạo mới
            UserPantry newItem = UserPantry.builder()
                    .userId(userId)
                    .ingredientId(ingredientId)
                    .quantity(qty)
                    .unit(unit)
                    .expiryDate(expiry)
                    .createdAt(LocalDateTime.now())
                    .build();
            pantryRepository.save(newItem);
        }
    }

    public void updatePantryItem(Integer pantryId, BigDecimal qty, LocalDate expiry) {
        UserPantry item = pantryRepository.findById(pantryId).orElseThrow();
        item.setQuantity(qty);
        item.setExpiryDate(expiry);
        pantryRepository.save(item);
    }

    public void removeFromPantry(Integer pantryId) {
        pantryRepository.deleteById(pantryId);
    }

    public List<PantryRecipeMatchDTO> suggestRecipes(Integer userId) {
        // 1. Lấy tất cả ID nguyên liệu trong tủ lạnh User
        List<UserPantry> pantryItems = pantryRepository.findByUserIdOrderByExpiryDateAsc(userId);
        List<Integer> myIngredientIds = pantryItems.stream()
                .map(UserPantry::getIngredientId)
                .toList();

        if (myIngredientIds.isEmpty()) {
            return new ArrayList<>();
        }

        // 2. Tìm các món ăn có chứa nguyên liệu này
        List<Recipe> candidates = recipeRepository.findRecipesByIngredients(myIngredientIds);
        List<PantryRecipeMatchDTO> results = new ArrayList<>();

        for (Recipe recipe : candidates) {
            // Lấy danh sách nguyên liệu YÊU CẦU của món ăn
            List<RecipeIngredient> required = recipeIngredientRepository.findByRecipeId(recipe.getRecipeId());

            if (required.isEmpty()) {
                continue;
            }

            int totalRequired = required.size();
            int haveCount = 0;

            for (RecipeIngredient req : required) {
                if (myIngredientIds.contains(req.getIngredientId())) {
                    haveCount++;
                }
            }

            // Tính % độ khớp
            int matchPercent = (int) (((double) haveCount / totalRequired) * 100);

            // Chỉ gợi ý nếu có ít nhất 30% nguyên liệu hoặc món đó ít nguyên liệu
            if (matchPercent > 30 || (totalRequired <= 3 && haveCount >= 1)) {
                PantryRecipeMatchDTO dto = new PantryRecipeMatchDTO();
                dto.setRecipeId(recipe.getRecipeId());
                dto.setName(recipe.getName());
                dto.setImageUrl(recipe.getImageUrl());
                dto.setCalories(recipe.getTotalCalories());
                dto.setTimeMin((recipe.getPrepTimeMin() != null ? recipe.getPrepTimeMin() : 0)
                        + (recipe.getCookTimeMin() != null ? recipe.getCookTimeMin() : 0));
                dto.setMatchPercentage(matchPercent);
                dto.setMissingCount(totalRequired - haveCount);

                results.add(dto);
            }
        }

        // Sắp xếp: Món nào khớp nhiều nhất lên đầu
        results.sort((a, b) -> b.getMatchPercentage() - a.getMatchPercentage());

        return results;
    }

    public List<PantryRecipeMatchDTO> suggestAiRecipes(Integer userId) {
        // A. Lấy nguyên liệu trong tủ
        List<UserPantry> pantry = pantryRepository.findByUserIdOrderByExpiryDateAsc(userId);
        if (pantry.isEmpty()) {
            return new ArrayList<>();
        }

        String ingredients = pantry.stream()
                .map(p -> p.getIngredient().getName() + "(" + p.getQuantity() + " " + p.getUnit() + ")")
                .collect(Collectors.joining(", "));

        // B. Lấy thông tin User
        UserProfile profile = userProfileRepository.findById(userId).orElse(null);
        String diet = dietaryRepo.findByUserId(userId).stream() // Giả sử bạn có hàm này
                .map(d -> d.getDietType().name())
                .collect(Collectors.joining(", "));

        String userInfo = (profile != null ? "Goal: " + profile.getGoal() : "") + ". Diet: " + diet;

        // C. Gọi AI Service
        return aiFoodService.generateRecipesFromPantry(ingredients, userInfo);
    }
}
