package com.group02.zaderfood.service;

import com.group02.zaderfood.entity.*;
import com.group02.zaderfood.entity.enums.RecipeStatus;
import com.group02.zaderfood.repository.*;
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
        if (updatedData.getRecipeIngredients() != null) {
            updateIngredientsList(existingRecipe, updatedData.getRecipeIngredients());
        }

        return recipeRepository.save(existingRecipe);
    }

    private void updateIngredientsList(Recipe recipe, List<RecipeIngredient> newItems) {
        // Lấy danh sách hiện tại trong DB
        List<RecipeIngredient> currentItems = recipeIngredientRepository.findByRecipeId(recipe.getRecipeId());

        // Map để tra cứu nhanh theo ID (nếu là update item cũ)
        Map<Integer, RecipeIngredient> currentMap = currentItems.stream()
                .filter(i -> i.getRecipeIngredientId() != null)
                .collect(Collectors.toMap(RecipeIngredient::getRecipeIngredientId, Function.identity()));

        List<RecipeIngredient> toSave = new ArrayList<>();
        List<Integer> keptIds = new ArrayList<>();

        for (RecipeIngredient newItem : newItems) {
            if (newItem.getRecipeIngredientId() != null && currentMap.containsKey(newItem.getRecipeIngredientId())) {
                // CASE A: Cập nhật món cũ
                RecipeIngredient existing = currentMap.get(newItem.getRecipeIngredientId());
                existing.setQuantity(newItem.getQuantity());
                existing.setUnit(newItem.getUnit());
                existing.setNote(newItem.getNote());
                existing.setUpdatedAt(LocalDateTime.now());
                toSave.add(existing);
                keptIds.add(existing.getRecipeIngredientId());
            } else {
                // CASE B: Thêm món mới vào list
                newItem.setRecipeId(recipe.getRecipeId());
                // Nếu User nhập text nguyên liệu mới (chưa có ID), ta cần tạo Ingredient mới trước
                if (newItem.getIngredient() != null && newItem.getIngredientId() == null) {
                    Ingredient newIngInfo = newItem.getIngredient();
                    // Logic tạo nhanh Ingredient
                    Ingredient createdIng = new Ingredient();
                    createdIng.setName(newIngInfo.getName());
                    createdIng.setCaloriesPer100g(newIngInfo.getCaloriesPer100g());
                    createdIng.setProtein(newIngInfo.getProtein());
                    createdIng.setFat(newIngInfo.getFat());
                    createdIng.setCarbs(newIngInfo.getCarbs());
                    createdIng.setIsActive(false); // Chờ duyệt
                    createdIng.setCreatedAt(LocalDateTime.now());

                    // Xử lý ảnh nguyên liệu nếu có
                    // (Cần DTO phức tạp hơn để hứng file ở đây, tạm thời bỏ qua ảnh ingredient con trong scope này để đơn giản)
                    ingredientRepository.save(createdIng);
                    newItem.setIngredientId(createdIng.getIngredientId());
                }

                newItem.setCreatedAt(LocalDateTime.now());
                newItem.setIsDeleted(false);
                toSave.add(newItem);
            }
        }

        // CASE C: Xóa những món không còn trong list gửi lên
        for (RecipeIngredient oldItem : currentItems) {
            if (!keptIds.contains(oldItem.getRecipeIngredientId())) {
                recipeIngredientRepository.delete(oldItem);
            }
        }

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
