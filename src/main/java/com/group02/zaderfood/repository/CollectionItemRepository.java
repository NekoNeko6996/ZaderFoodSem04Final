package com.group02.zaderfood.repository;

import com.group02.zaderfood.entity.CollectionItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CollectionItemRepository extends JpaRepository<CollectionItem, Integer> {
    boolean existsByCollectionIdAndRecipeId(Integer collectionId, Integer recipeId);
}