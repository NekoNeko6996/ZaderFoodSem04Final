package com.group02.zaderfood.service;

import com.group02.zaderfood.entity.*;
import com.group02.zaderfood.entity.enums.RecipeStatus;
import com.group02.zaderfood.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

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

        // Lưu ý: Việc cập nhật List<RecipeIngredient> và List<RecipeStep> phức tạp hơn
        // cần logic xóa cũ thêm mới hoặc update từng item. 
        // Trong phạm vi code mẫu này, ta tập trung vào logic duyệt/từ chối.
        return recipeRepository.save(existingRecipe);
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
        step.setUpdatedAt(LocalDateTime.now());

        recipeStepRepository.save(step);
    }
}
