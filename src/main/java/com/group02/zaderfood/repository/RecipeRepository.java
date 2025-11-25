package com.group02.zaderfood.repository;

import com.group02.zaderfood.entity.Recipe;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface RecipeRepository extends JpaRepository<Recipe, Integer> {
    // Thêm đoạn này: Tìm các Recipe có chứa ít nhất 1 trong các ingredientIds truyền vào
    @Query("SELECT DISTINCT r FROM Recipe r " +
           "JOIN r.recipeIngredients ri " +
           "WHERE ri.ingredientId IN :ids " +
           "AND r.isDeleted = false")
    List<Recipe> findRecipesByIngredientIds(@Param("ids") List<Integer> ids);
}