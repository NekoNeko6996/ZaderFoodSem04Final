package com.group02.zaderfood.service;

import com.group02.zaderfood.dto.IngredientInputDTO;
import com.group02.zaderfood.entity.Ingredient;
import com.group02.zaderfood.entity.IngredientCategory;
import com.group02.zaderfood.repository.IngredientCategoryRepository;
import com.group02.zaderfood.repository.IngredientRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminIngredientService {

    @Autowired
    private IngredientRepository ingredientRepository;
    @Autowired
    private IngredientCategoryRepository categoryRepository;
    @Autowired
    private IngredientService ingredientService; // Tái sử dụng logic thêm mới

    // 1. Tìm kiếm và phân trang
    public Page<Ingredient> getIngredients(String keyword, Integer categoryId, Boolean isActive, int page, int size) {
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by("createdAt").descending());
        return ingredientRepository.searchIngredients(keyword, categoryId, isActive, pageable);
    }

    // 2. Lấy tất cả danh mục (cho dropdown filter)
    public List<IngredientCategory> getAllCategories() {
        return categoryRepository.findAll();
    }

    // 3. Thêm mới nguyên liệu (Gọi lại service cũ để tái sử dụng logic upload ảnh)
    public void createIngredient(IngredientInputDTO dto, Integer adminId) {
        ingredientService.createSystemIngredient(dto, adminId);
    }

    // 4. Đổi trạng thái Active/Inactive
    public void toggleStatus(Integer id) {
        Ingredient ing = ingredientRepository.findById(id).orElseThrow(() -> new RuntimeException("Not found"));
        ing.setIsActive(!Boolean.TRUE.equals(ing.getIsActive()));
        ingredientRepository.save(ing);
    }

    // 5. Xóa mềm (Soft Delete)
    public void deleteIngredient(Integer id) {
        Ingredient ing = ingredientRepository.findById(id).orElseThrow(() -> new RuntimeException("Not found"));
        ing.setIsDeleted(true);
        ing.setDeletedAt(LocalDateTime.now());
        ingredientRepository.save(ing);
    }

    // --- QUẢN LÝ CATEGORY ---
    public void createCategory(String name) {
        IngredientCategory cat = new IngredientCategory();
        cat.setName(name);
        cat.setCreatedAt(LocalDateTime.now());
        cat.setIsDeleted(false);
        categoryRepository.save(cat);
    }

    /**
     * Ưu tiên Xóa Cứng (Hard Delete). Nếu dính khóa ngoại (đang được sử dụng)
     * -> Chuyển sang Xóa Mềm (Soft Delete).
     *
     * @return String thông báo trạng thái để Controller biết đường hiển thị
     */
    @Transactional
    public String deleteCategorySmart(Integer id) {
        IngredientCategory cat = categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Category not found"));

        try {
            // 1. Thử xóa cứng
            categoryRepository.delete(cat);

            categoryRepository.flush();

            return "HARD"; // Đã xóa vĩnh viễn

        } catch (DataIntegrityViolationException e) {

            cat.setIsDeleted(true);
            cat.setDeletedAt(LocalDateTime.now());
            categoryRepository.save(cat);

            return "SOFT"; // Đã chuyển sang ẩn
        }
    }

    public void updateCategory(Integer id, String newName) {
        IngredientCategory cat = categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Category not found"));

        cat.setName(newName);
        cat.setUpdatedAt(LocalDateTime.now());

        categoryRepository.save(cat);
    }
}
