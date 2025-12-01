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
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

import com.group02.zaderfood.dto.RecipeMatchDTO;
import com.group02.zaderfood.entity.CollectionItem;
import com.group02.zaderfood.entity.RecipeCollection;
import com.group02.zaderfood.repository.CollectionItemRepository;
import com.group02.zaderfood.repository.RecipeCollectionRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Set;
import java.util.HashSet;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@Service
public class RecipeService {

    @Autowired
    private RecipeRepository recipeRepository;
    @Autowired
    private IngredientRepository ingredientRepository;
    @Autowired
    private RecipeIngredientRepository recipeIngredientRepository;
    @Autowired
    private RecipeStepRepository recipeStepRepository;
    @Autowired
    private FileStorageService fileStorageService;
    @Autowired
    private RecipeCollectionRepository collectionRepository;
    @Autowired
    private CollectionItemRepository collectionItemRepository;

    @Transactional
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

    public List<Recipe> findRecipesByIngredientIds(List<Integer> ingredientIds) {
        if (ingredientIds == null || ingredientIds.isEmpty()) {
            return List.of(); // Trả về list rỗng nếu không chọn gì
        }
        return recipeRepository.findRecipesByIngredientIds(ingredientIds);
    }

    public List<Recipe> searchAndFilterRecipes(List<Integer> ingredientIds, String keyword, Integer maxCalories, Integer maxTime, String difficulty) {
        List<Recipe> recipes;

        // 1. Lấy danh sách ban đầu (Theo nguyên liệu HOẶC Lấy tất cả)
        if (ingredientIds != null && !ingredientIds.isEmpty()) {
            recipes = recipeRepository.findRecipesByIngredientIds(ingredientIds);
        } else {
            recipes = recipeRepository.findAllActiveRecipes();
        }

        // 2. Lọc bằng Java Stream (Đơn giản và hiệu quả với dữ liệu vừa phải)
        return recipes.stream()
                // Lọc theo tên (Keyword)
                .filter(r -> keyword == null || keyword.isEmpty()
                || r.getName().toLowerCase().contains(keyword.toLowerCase()))
                // Lọc theo Calories (Nhỏ hơn hoặc bằng)
                .filter(r -> maxCalories == null
                || (r.getTotalCalories() != null && r.getTotalCalories().intValue() <= maxCalories))
                // Lọc theo Thời gian (Nhỏ hơn hoặc bằng)
                .filter(r -> maxTime == null
                || (r.getCookTimeMin() != null && r.getCookTimeMin() <= maxTime))
                // Lọc theo Độ khó
                .filter(r -> difficulty == null || difficulty.isEmpty()
                || (r.getDifficulty() != null && r.getDifficulty().name().equalsIgnoreCase(difficulty)))
                .collect(Collectors.toList());
    }

    public List<RecipeMatchDTO> findRecipesWithMissingIngredients(List<Integer> userIngredientIds, String keyword, Integer maxCalories, Integer maxTime, String difficulty) {
        List<RecipeMatchDTO> results = new ArrayList<>();

        List<Recipe> allRecipes = recipeRepository.findAllActiveRecipes();

        // Set chứa ID nguyên liệu của user (để tra cứu nhanh)
        Set<Integer> userOwnedIds = (userIngredientIds == null) ? new HashSet<>() : new HashSet<>(userIngredientIds);
        boolean isIngredientSearch = !userOwnedIds.isEmpty(); // Cờ đánh dấu xem có phải đang tìm theo nguyên liệu không

        for (Recipe recipe : allRecipes) {
            // --- 1. LỌC CƠ BẢN (Keyword, Calo, Time, Difficulty) ---
            // Giữ nguyên logic lọc cũ
            if (keyword != null && !keyword.isEmpty() && !recipe.getName().toLowerCase().contains(keyword.toLowerCase())) {
                continue;
            }
            if (maxCalories != null && recipe.getTotalCalories() != null && recipe.getTotalCalories().intValue() > maxCalories) {
                continue;
            }
            if (maxTime != null && recipe.getCookTimeMin() != null && recipe.getCookTimeMin() > maxTime) {
                continue;
            }
            if (difficulty != null && !difficulty.isEmpty() && recipe.getDifficulty() != null && !recipe.getDifficulty().name().equalsIgnoreCase(difficulty)) {
                continue;
            }

            // --- 2. XỬ LÝ LOGIC ---
            if (isIngredientSearch) {
                // CASE A: Có chọn nguyên liệu -> Tính toán so sánh
                List<String> missingNames = new ArrayList<>();
                int totalIngredients = 0;
                int matchedCount = 0;

                if (recipe.getRecipeIngredients() != null) {
                    totalIngredients = recipe.getRecipeIngredients().size();
                    for (RecipeIngredient ri : recipe.getRecipeIngredients()) {
                        if (userOwnedIds.contains(ri.getIngredientId())) {
                            matchedCount++;
                        } else {
                            if (ri.getIngredient() != null) {
                                missingNames.add(ri.getIngredient().getName());
                            }
                        }
                    }
                }

                // Chỉ thêm vào danh sách nếu có ít nhất 1 nguyên liệu trùng khớp
                if (matchedCount > 0 || totalIngredients == 0) {
                    int percentage = (totalIngredients == 0) ? 100 : (int) ((double) matchedCount / totalIngredients * 100);
                    results.add(new RecipeMatchDTO(recipe, percentage, missingNames.size(), missingNames));
                }

            } else {
                // CASE B: Truy cập trực tiếp (Không chọn nguyên liệu) -> Không tính toán
                // Set matchPercentage = 0 hoặc 100 tùy ý (nhưng View sẽ ẩn nó đi)
                // missingCount = 0
                results.add(new RecipeMatchDTO(recipe, 0, 0, new ArrayList<>()));
            }
        }

        // --- 3. SẮP XẾP ---
        if (isIngredientSearch) {
            // Nếu tìm theo nguyên liệu: Ưu tiên % cao nhất
            results.sort(Comparator.comparingInt(RecipeMatchDTO::getMatchPercentage).reversed()
                    .thenComparingInt(RecipeMatchDTO::getMissingCount));
        } else {
            // Nếu xem danh sách thường: Có thể sắp xếp theo tên hoặc ID (tùy bạn)
            // Ví dụ: Sắp xếp theo ID giảm dần (Mới nhất lên đầu)
            results.sort((r1, r2) -> r2.getRecipe().getRecipeId().compareTo(r1.getRecipe().getRecipeId()));
        }

        return results;
    }

    @Transactional
    public boolean toggleFavorite(Integer userId, Integer recipeId) {
        String defaultCollectionName = "Recipe Favorite";

        // 1. Tìm hoặc Tạo Collection "Recipe Favorite"
        RecipeCollection collection = collectionRepository.findByUserIdAndName(userId, defaultCollectionName)
                .orElseGet(() -> {
                    RecipeCollection newCol = new RecipeCollection();
                    newCol.setUserId(userId);
                    newCol.setName(defaultCollectionName);
                    newCol.setIsPublic(false);
                    newCol.setCreatedAt(LocalDateTime.now());
                    newCol.setUpdatedAt(LocalDateTime.now());
                    newCol.setIsDeleted(false);
                    return collectionRepository.save(newCol);
                });

        // 2. Kiểm tra món ăn đã có trong collection chưa
        boolean exists = collectionItemRepository.existsByCollectionIdAndRecipeId(collection.getCollectionId(), recipeId);

        if (exists) {
            // (Tuỳ chọn) Nếu muốn bấm lần nữa là XÓA (Toggle) thì viết code xóa ở đây
            // Hiện tại yêu cầu là "Thêm", nên nếu có rồi thì ta có thể trả về false hoặc true tùy ý
            return false; // Đã tồn tại
        } else {
            // 3. Thêm vào CollectionItems
            CollectionItem item = new CollectionItem();
            item.setCollectionId(collection.getCollectionId());
            item.setRecipeId(recipeId);
            item.setAddedAt(LocalDateTime.now());
            item.setCreatedAt(LocalDateTime.now());
            item.setUpdatedAt(LocalDateTime.now());
            item.setIsDeleted(false);

            collectionItemRepository.save(item);
            return true; // Thêm mới thành công
        }
    }

    public List<Recipe> getLatestRecipes(int limit) {
        Pageable pageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt"));
        return recipeRepository.findByStatus(RecipeStatus.ACTIVE, pageable);
    }
}
