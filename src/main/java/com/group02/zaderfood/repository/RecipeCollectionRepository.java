package com.group02.zaderfood.repository;

import com.group02.zaderfood.entity.RecipeCollection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface RecipeCollectionRepository extends JpaRepository<RecipeCollection, Integer> {
    Optional<RecipeCollection> findByUserIdAndName(Integer userId, String name);
    List<RecipeCollection> findByUserId(Integer userId);
}