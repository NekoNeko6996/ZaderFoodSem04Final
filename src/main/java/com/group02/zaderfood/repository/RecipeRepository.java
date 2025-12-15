package com.group02.zaderfood.repository;

import com.group02.zaderfood.entity.Recipe;
import com.group02.zaderfood.entity.enums.RecipeStatus;
import java.math.BigDecimal;
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

    List<Recipe> findTop50ByStatusAndTotalCaloriesLessThanEqual(RecipeStatus status, BigDecimal maxCalories);

    // Nếu TotalCalories trong DB chưa chuẩn (bằng 0 hoặc null), ta lấy 50 món bất kỳ để tính toán lại:
    List<Recipe> findTop50ByStatus(RecipeStatus status);

    @Query(value = "SELECT TOP 50 * FROM Recipes "
            + "WHERE IsNutritionist = 1 "
            + "AND Status = 'ACTIVE' "
            + "AND (IsDeleted = 0 OR IsDeleted IS NULL) "
            + "ORDER BY NEWID()", nativeQuery = true)
    List<Recipe> findRandomNutritionistRecipes();

    long countByStatus(RecipeStatus status);

    @Query("SELECT r.difficulty, COUNT(r) FROM Recipe r WHERE r.status = 'ACTIVE' GROUP BY r.difficulty")
    List<Object[]> countRecipesByDifficulty();

    @Query("SELECT r FROM Recipe r WHERE "
            + "(:keyword IS NULL OR :keyword = '' OR r.name LIKE %:keyword%) "
            + "AND (:status IS NULL OR r.status = :status) "
            + "AND (:maxCalories IS NULL OR r.totalCalories <= :maxCalories) "
            + "AND (r.isDeleted = false OR r.isDeleted IS NULL) "
            + "ORDER BY r.createdAt DESC")
    List<Recipe> searchRecipes(@Param("keyword") String keyword,
            @Param("status") RecipeStatus status,
            @Param("maxCalories") Integer maxCalories);
    
    @Query("SELECT DISTINCT r FROM Recipe r " +
           "JOIN r.recipeIngredients ri " +
           "WHERE ri.ingredientId IN :ingredientIds " +
           "AND r.status = 'ACTIVE' " +
           "AND (r.isDeleted = false OR r.isDeleted IS NULL)")
    List<Recipe> findRecipesByIngredients(@Param("ingredientIds") List<Integer> ingredientIds);
}
