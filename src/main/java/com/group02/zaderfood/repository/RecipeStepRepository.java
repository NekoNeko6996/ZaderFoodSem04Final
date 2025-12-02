package com.group02.zaderfood.repository;

import com.group02.zaderfood.entity.RecipeStep;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RecipeStepRepository extends JpaRepository<RecipeStep, Integer> {
    List<RecipeStep> findByRecipeId(Integer recipeId);
    
    // Service đang gọi hàm này để xóa các bước khi reject
    void deleteAllByRecipeId(Integer recipeId);
}