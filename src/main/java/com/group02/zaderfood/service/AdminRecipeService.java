package com.group02.zaderfood.service;

import com.group02.zaderfood.entity.*;
import com.group02.zaderfood.entity.enums.RecipeStatus;
import com.group02.zaderfood.repository.*;
import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;

@Service
public class AdminRecipeService {

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

    /**
     * Lấy danh sách các công thức đang chờ duyệt (PENDING)
     */
    public List<Recipe> getPendingRecipes() {
        return recipeRepository.findByStatusAndIsDeletedFalse(RecipeStatus.PENDING);
    }

    /**
     * Lấy chi tiết công thức bao gồm nguyên liệu và các bước (Lưu ý: Hibernate
     * Lazy loading cần được xử lý hoặc dùng DTO, ở đây trả về Entity trực tiếp
     * để đơn giản hóa ví dụ)
     */
    public Recipe getRecipeDetail(Integer recipeId) {
        return recipeRepository.findById(recipeId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy công thức"));
    }

    /**
     * Admin cập nhật thông tin công thức trước khi duyệt (Chỉnh sửa tên, mô tả,
     * định lượng...)
     */
    @Transactional
    public Recipe updateRecipeContent(Integer recipeId, Recipe updatedData) {
        Recipe existingRecipe = getRecipeDetail(recipeId);

        // Cập nhật thông tin cơ bản
        existingRecipe.setName(updatedData.getName());
        existingRecipe.setDescription(updatedData.getDescription());
        existingRecipe.setPrepTimeMin(updatedData.getPrepTimeMin());
        existingRecipe.setCookTimeMin(updatedData.getCookTimeMin());
        existingRecipe.setServings(updatedData.getServings());
        existingRecipe.setTotalCalories(updatedData.getTotalCalories());
        existingRecipe.setDifficulty(updatedData.getDifficulty());
        existingRecipe.setUpdatedAt(LocalDateTime.now());

        if (updatedData.getImageFile() != null && !updatedData.getImageFile().isEmpty()) {
            String newImageUrl = fileStorageService.storeFile(updatedData.getImageFile());
            existingRecipe.setImageUrl(newImageUrl);
        }

        // 3. Xử lý danh sách nguyên liệu (SMART MERGE)
        updateIngredientsList(existingRecipe, updatedData.getRecipeIngredients());

        return recipeRepository.save(existingRecipe);
    }

    private void updateIngredientsList(Recipe recipe, List<RecipeIngredient> newItems) {
        // [DEBUG]
        System.out.println("========== SERVICE LOGIC ==========");
        if (newItems == null) {
            return;
        }

        List<RecipeIngredient> currentItems = recipeIngredientRepository.findByRecipeId(recipe.getRecipeId());
        System.out.println("Current DB Items: " + currentItems.size());

        // Map để tra cứu nhanh theo ID
        Map<Integer, RecipeIngredient> currentMap = currentItems.stream()
                .filter(i -> i.getRecipeIngredientId() != null)
                .collect(Collectors.toMap(RecipeIngredient::getRecipeIngredientId, Function.identity()));

        List<RecipeIngredient> toSave = new ArrayList<>();
        List<Integer> keptIds = new ArrayList<>();

        for (RecipeIngredient newItem : newItems) {
            // Fix Null Pointer như đã bàn ở bước trước
            if (newItem == null) {
                continue;
            }

            if (newItem.getRecipeIngredientId() != null && currentMap.containsKey(newItem.getRecipeIngredientId())) {
                System.out.println(" -> UPDATE existing item: " + newItem.getRecipeIngredientId());
                RecipeIngredient existing = currentMap.get(newItem.getRecipeIngredientId());
                existing.setQuantity(newItem.getQuantity());
                existing.setUnit(newItem.getUnit());
                existing.setNote(newItem.getNote());
                existing.setUpdatedAt(LocalDateTime.now());
                toSave.add(existing);
                keptIds.add(existing.getRecipeIngredientId());
            } else {
                System.out.println(" -> CREATE NEW item logic");
                newItem.setRecipeId(recipe.getRecipeId());

                // Logic thêm Ingredient mới
                if (newItem.getIngredient() != null && newItem.getIngredientId() == null) {
                    Ingredient newIngInfo = newItem.getIngredient();

                    Ingredient createdIng = new Ingredient();
                    createdIng.setName(newIngInfo.getName());

                    createdIng.setCaloriesPer100g(newIngInfo.getCaloriesPer100g() != null ? newIngInfo.getCaloriesPer100g() : BigDecimal.ZERO);
                    createdIng.setProtein(newIngInfo.getProtein() != null ? newIngInfo.getProtein() : BigDecimal.ZERO);
                    createdIng.setFat(newIngInfo.getFat() != null ? newIngInfo.getFat() : BigDecimal.ZERO);
                    createdIng.setCarbs(newIngInfo.getCarbs() != null ? newIngInfo.getCarbs() : BigDecimal.ZERO);

                    // Lấy BaseUnit
                    createdIng.setBaseUnit(newIngInfo.getBaseUnit() != null ? newIngInfo.getBaseUnit() : "g");

                    createdIng.setIsActive(false);
                    createdIng.setCreatedAt(LocalDateTime.now());

                    ingredientRepository.save(createdIng);

                    newItem.setIngredientId(createdIng.getIngredientId());
                } else {
                    System.out.println("    -> Using existing Ingredient ID: " + newItem.getIngredientId());
                }

                newItem.setIngredient(null);

                newItem.setCreatedAt(LocalDateTime.now());
                newItem.setIsDeleted(false);
                toSave.add(newItem);
            }
        }

        System.out.println("--- CHECKING FOR DELETIONS ---");
        System.out.println("Ids to Keep: " + keptIds);

        List<RecipeIngredient> toDelete = new ArrayList<>();
        for (RecipeIngredient oldItem : currentItems) {
            // Nếu ID trong DB không nằm trong danh sách Giữ lại -> XÓA
            if (!keptIds.contains(oldItem.getRecipeIngredientId())) {
                System.out.println(" >> DETECTED DELETE FOR ID: " + oldItem.getRecipeIngredientId());
                toDelete.add(oldItem);
            }
        }

        if (!toDelete.isEmpty()) {
            recipeIngredientRepository.deleteAll(toDelete);
            recipeIngredientRepository.flush(); // [QUAN TRỌNG] Ép thực thi lệnh DELETE ngay lập tức
            System.out.println(" >> FLUSHED DELETIONS TO DB");
        }

        // 4. Lưu danh sách thêm/sửa
        if (!toSave.isEmpty()) {
            recipeIngredientRepository.saveAll(toSave);
        }

        System.out.println("Saving total items: " + toSave.size());
        recipeIngredientRepository.saveAll(toSave);
    }

    /**
     * DUYỆT CÔNG THỨC 1. Chuyển trạng thái Recipe -> ACTIVE 2. Tìm các
     * Ingredient liên quan, nếu đang inactive (mới tạo) -> ACTIVE
     */
    @Transactional
    public void approveRecipe(Integer recipeId) {
        Recipe recipe = getRecipeDetail(recipeId);

        // 1. Cập nhật trạng thái công thức
        recipe.setStatus(RecipeStatus.ACTIVE);
        recipe.setUpdatedAt(LocalDateTime.now());
        recipeRepository.save(recipe);

        // 2. Kích hoạt các nguyên liệu mới đi kèm
        List<RecipeIngredient> recipeIngredients = recipeIngredientRepository.findByRecipeId(recipeId);
        for (RecipeIngredient ri : recipeIngredients) {
            Ingredient ingredient = ri.getIngredient();
            // Nếu nguyên liệu chưa active (nguyên liệu mới do user đóng góp)
            if (ingredient != null && Boolean.FALSE.equals(ingredient.getIsActive())) {
                ingredient.setIsActive(true);
                ingredient.setUpdatedAt(LocalDateTime.now());
                ingredientRepository.save(ingredient);
            }
        }
    }

    /**
     * TỪ CHỐI CÔNG THỨC 1. Xóa Recipe (Soft delete hoặc Hard delete tùy policy,
     * ở đây làm theo yêu cầu: Xóa khỏi DB) 2. Xóa các Ingredient mới đi kèm
     * (chưa active)
     */
    @Transactional
    public void rejectRecipe(Integer recipeId) {
        Recipe recipe = getRecipeDetail(recipeId);

        // Lấy danh sách liên kết nguyên liệu trước khi xóa công thức
        List<RecipeIngredient> recipeIngredients = recipeIngredientRepository.findByRecipeId(recipeId);

        // 1. Xóa các thành phần con (Steps, RecipeIngredients)
        // Nếu database có cấu hình Cascade Delete thì bước này tự động, nếu không phải xóa tay:
        recipeStepRepository.deleteAllByRecipeId(recipeId);
        recipeIngredientRepository.deleteAllByRecipeId(recipeId);

        // 2. Xóa các nguyên liệu MỚI (chưa được duyệt) đi kèm
        for (RecipeIngredient ri : recipeIngredients) {
            Ingredient ingredient = ri.getIngredient();
            if (ingredient != null && Boolean.FALSE.equals(ingredient.getIsActive())) {
                // Kiểm tra xem nguyên liệu này có được dùng bởi công thức nào khác không trước khi xóa (Optional - an toàn dữ liệu)
                // Ở đây giả định user tạo mới chỉ cho công thức này nên xóa luôn.
                ingredientRepository.delete(ingredient);
            }
        }

        // 3. Xóa công thức chính
        recipeRepository.delete(recipe);
    }

    public void updateStepInstruction(Integer stepId, String newInstruction) {
        RecipeStep step = recipeStepRepository.findById(stepId)
                .orElseThrow(() -> new RuntimeException("Step not found"));

        step.setInstruction(newInstruction);

        recipeStepRepository.save(step);
    }

    public List<Recipe> getAllRecipes() {
        // Lấy tất cả, sắp xếp mới nhất lên đầu
        return recipeRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    @Transactional
    public String deleteRecipeSmart(Integer id) {
        Recipe recipe = recipeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Recipe not found"));

        try {
            // 1. Xóa các thành phần con trước (Steps, Ingredients liên kết)
            // Lưu ý: Nếu DB cấu hình Cascade Delete thì bước này tự động.
            // Nếu không, phải xóa tay. Ở đây ta thử xóa cứng Recipe.

            // Nếu muốn xóa cứng sạch sẽ, phải xóa bảng con trước:
            recipeStepRepository.deleteAllByRecipeId(id);
            recipeIngredientRepository.deleteAllByRecipeId(id);

            recipeRepository.delete(recipe);
            recipeRepository.flush(); // Ép thực thi SQL ngay

            return "HARD"; // Xóa vĩnh viễn thành công

        } catch (DataIntegrityViolationException e) {
            // 2. Nếu dính khóa ngoại (ví dụ: đã có trong MealPlan hoặc Reviews), chuyển sang xóa mềm
            recipe.setIsDeleted(true);
            recipe.setDeletedAt(LocalDateTime.now());
            recipe.setStatus(RecipeStatus.HIDDEN); // Ẩn khỏi hiển thị
            recipeRepository.save(recipe);

            return "SOFT"; // Chuyển sang lưu trữ
        }
    }

    public List<Recipe> searchRecipes(String keyword, RecipeStatus status, Integer maxCalories) {
        // Nếu maxCalories <= 0 thì coi như không lọc calo
        if (maxCalories != null && maxCalories <= 0) {
            maxCalories = null;
        }
        return recipeRepository.searchRecipes(keyword, status, maxCalories);
    }
}
