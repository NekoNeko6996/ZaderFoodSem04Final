package com.group02.zaderfood.repository;

import com.group02.zaderfood.entity.Recipe;
import com.group02.zaderfood.entity.enums.RecipeStatus;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface RecipeRepository extends JpaRepository<Recipe, Integer> {

    // 1. Tìm theo nguyên liệu
    @Query("SELECT DISTINCT r FROM Recipe r "
            + "JOIN r.recipeIngredients ri "
            + "WHERE ri.ingredientId IN :ids "
            + "AND (r.isDeleted IS NULL OR r.isDeleted = false) "
            + "AND r.status = com.group02.zaderfood.entity.enums.RecipeStatus.ACTIVE")
    List<Recipe> findRecipesByIngredientIds(@Param("ids") List<Integer> ids);

    // 2. Tìm tất cả Active
    @Query("SELECT r FROM Recipe r WHERE (r.isDeleted IS NULL OR r.isDeleted = false) AND r.status = com.group02.zaderfood.entity.enums.RecipeStatus.ACTIVE")
    List<Recipe> findAllActiveRecipes();

    List<Recipe> findByStatus(RecipeStatus status, Pageable pageable);

    // 3. Tìm theo tên
    @Query("SELECT r FROM Recipe r WHERE r.name LIKE %:keyword% AND (r.isDeleted IS NULL OR r.isDeleted = false) AND r.status = com.group02.zaderfood.entity.enums.RecipeStatus.ACTIVE")
    List<Recipe> findByNameContainingAndActive(@Param("keyword") String keyword);

    @Query("SELECT r FROM Recipe r WHERE r.status = :status AND (r.isDeleted IS NULL OR r.isDeleted = false)")
    List<Recipe> findByStatusAndIsDeletedFalse(@Param("status") RecipeStatus status);
    
    @Query(value = "SELECT TOP 50 * FROM Recipes ORDER BY NEWID()", nativeQuery = true)
    List<Recipe> findRandomRecipes();
}
