package com.group02.zaderfood.repository;

import com.group02.zaderfood.entity.RecipeIngredient;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RecipeIngredientRepository extends JpaRepository<RecipeIngredient, Integer> {
    List<RecipeIngredient> findByRecipeId(Integer recipeId);

    // Hàm xóa theo recipeId
    void deleteAllByRecipeId(Integer recipeId);
}