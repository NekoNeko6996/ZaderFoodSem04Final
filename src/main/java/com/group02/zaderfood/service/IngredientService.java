package com.group02.zaderfood.service;

// File: service/IngredientService.java
import com.group02.zaderfood.dto.IngredientInputDTO;
import com.group02.zaderfood.entity.Ingredient;
import com.group02.zaderfood.entity.IngredientCategory;
import com.group02.zaderfood.repository.IngredientCategoryRepository;
import com.group02.zaderfood.repository.IngredientRepository;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.List;

@Service
public class IngredientService {

    @Autowired
    private IngredientRepository ingredientRepository;
    @Autowired
    private IngredientCategoryRepository categoryRepository;
    @Autowired
    private FileStorageService fileStorageService;

    // Lấy danh sách nguyên liệu có sẵn để user chọn
    public List<Ingredient> findAllActiveIngredients() {
        return ingredientRepository.findAllActive();
    }

    // Lấy danh mục (Thịt, cá, rau...) để user chọn khi thêm mới
    public List<IngredientCategory> findAllCategories() {
        return categoryRepository.findAll();
    }

    public void requestNewIngredient(IngredientInputDTO form, Integer userId) {
        Ingredient ingredient = new Ingredient();

        ingredient.setName(form.getNewName());
        ingredient.setCategoryId(form.getCategoryId());
        ingredient.setCaloriesPer100g(form.getCaloriesPer100g());

        // --- BỔ SUNG MAP DỮ LIỆU CÒN THIẾU ---
        ingredient.setProtein(form.getProtein());
        ingredient.setCarbs(form.getCarbs());
        ingredient.setFat(form.getFat());
        ingredient.setBaseUnit(form.getBaseUnit());
        // -------------------------------------

        if (form.getNewIngredientImage() != null && !form.getNewIngredientImage().isEmpty()) {
            String imageUrl = fileStorageService.storeFile(form.getNewIngredientImage());
            ingredient.setImageUrl(imageUrl);
        }

        ingredient.setIsActive(false);
        ingredient.setCreatedByUserId(userId);
        ingredient.setCreatedAt(LocalDateTime.now());
        ingredient.setUpdatedAt(LocalDateTime.now());
        ingredient.setIsDeleted(false);

        ingredientRepository.save(ingredient);
    }

    public void createSystemIngredient(IngredientInputDTO form, Integer userId) {
        Ingredient ingredient = new Ingredient();

        // Map data cơ bản
        ingredient.setName(form.getNewName());
        ingredient.setCategoryId(form.getCategoryId());
        ingredient.setCaloriesPer100g(form.getCaloriesPer100g());

        // --- BỔ SUNG MAP DỮ LIỆU CÒN THIẾU TẠI ĐÂY ---
        // Lý do bị NULL là do thiếu 4 dòng này:
        ingredient.setProtein(form.getProtein());
        ingredient.setCarbs(form.getCarbs());
        ingredient.setFat(form.getFat());
        ingredient.setBaseUnit(form.getBaseUnit());
        // ---------------------------------------------

        // Handle image (Nếu Admin có upload ảnh thì cũng cần map vào)
        if (form.getNewIngredientImage() != null && !form.getNewIngredientImage().isEmpty()) {
            String imageUrl = fileStorageService.storeFile(form.getNewIngredientImage());
            ingredient.setImageUrl(imageUrl);
        }

        ingredient.setIsActive(true);
        ingredient.setCreatedByUserId(userId);

        ingredient.setCreatedAt(LocalDateTime.now());
        ingredient.setUpdatedAt(LocalDateTime.now());
        ingredient.setIsDeleted(false);

        ingredientRepository.save(ingredient);
    }
}
