package com.group02.zaderfood.service;

// File: service/IngredientService.java
import com.group02.zaderfood.entity.Ingredient;
import com.group02.zaderfood.entity.IngredientCategory;
import com.group02.zaderfood.repository.IngredientCategoryRepository;
import com.group02.zaderfood.repository.IngredientRepository;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.List;

@Service
public class IngredientService {

    @Autowired
    private IngredientRepository ingredientRepository;
    
    @Autowired
    private IngredientCategoryRepository categoryRepository;

    // Lấy danh sách nguyên liệu có sẵn để user chọn
    public List<Ingredient> findAllActiveIngredients() {
        return ingredientRepository.findAllActive();
    }

    // Lấy danh mục (Thịt, cá, rau...) để user chọn khi thêm mới
    public List<IngredientCategory> findAllCategories() {
        return categoryRepository.findAll();
    }
}