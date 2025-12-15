package com.group02.zaderfood.repository;

import com.group02.zaderfood.entity.AiSavedRecipes;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface AiSavedRecipeRepository extends JpaRepository<AiSavedRecipes, Integer> {

    // Tìm các món AI đã lưu của user, sắp xếp mới nhất lên đầu
    List<AiSavedRecipes> findByUserIdOrderBySavedAtDesc(Integer userId);
}
