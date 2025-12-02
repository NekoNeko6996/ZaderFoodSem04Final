package com.group02.zaderfood.repository;

import com.group02.zaderfood.entity.CollectionItem;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CollectionItemRepository extends JpaRepository<CollectionItem, Integer> {
    boolean existsByCollectionIdAndRecipeId(Integer collectionId, Integer recipeId);
    
    @Query("SELECT c.recipeId FROM CollectionItem c WHERE c.collectionId = :collectionId AND (c.isDeleted = false OR c.isDeleted IS NULL)")
    List<Integer> findRecipeIdsByCollectionId(@Param("collectionId") Integer collectionId);
    
    @Query("SELECT COUNT(c) FROM CollectionItem c WHERE c.collectionId = :collectionId AND (c.isDeleted = false OR c.isDeleted IS NULL)")
    int countByCollectionId(@Param("collectionId") Integer collectionId);

    // Kiểm tra tồn tại để xóa (cho chức năng remove khỏi collection)
    Optional<CollectionItem> findByCollectionIdAndRecipeId(Integer collectionId, Integer recipeId);
    
    void deleteByCollectionId(Integer collectionId);
}