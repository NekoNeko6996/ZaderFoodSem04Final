package com.group02.zaderfood.repository;

import com.group02.zaderfood.entity.Ingredient;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface IngredientRepository extends JpaRepository<Ingredient, Integer> {

    @Query("SELECT i FROM Ingredient i WHERE i.isActive = true")
    List<Ingredient> findAllActive();

    @Query("SELECT i FROM Ingredient i WHERE "
            + "(:keyword IS NULL OR :keyword = '' OR i.name LIKE %:keyword%) AND "
            + "(:categoryId IS NULL OR i.categoryId = :categoryId) AND "
            + "(:isActive IS NULL OR i.isActive = :isActive) AND "
            + "(i.isDeleted = false OR i.isDeleted IS NULL)")
            
    Page<Ingredient> searchIngredients(@Param("keyword") String keyword,
            @Param("categoryId") Integer categoryId,
            @Param("isActive") Boolean isActive,
            Pageable pageable);
}
