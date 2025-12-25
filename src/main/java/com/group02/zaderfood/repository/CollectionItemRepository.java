package com.group02.zaderfood.repository;

import com.group02.zaderfood.entity.CollectionItem;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CollectionItemRepository extends JpaRepository<CollectionItem, Integer> {

    boolean existsByCollectionIdAndRecipeId(Integer collectionId, Integer recipeId);

    @Query("SELECT c.recipeId FROM CollectionItem c WHERE c.collectionId = :collectionId")
    List<Integer> findRecipeIdsByCollectionId(@Param("collectionId") Integer collectionId);

    @Query("SELECT COUNT(c) FROM CollectionItem c WHERE c.collectionId = :collectionId")
    int countByCollectionId(@Param("collectionId") Integer collectionId);

    // Kiểm tra tồn tại để xóa (cho chức năng remove khỏi collection)
    Optional<CollectionItem> findByCollectionIdAndRecipeId(Integer collectionId, Integer recipeId);

    void deleteByCollectionId(Integer collectionId);

    List<CollectionItem> findByCollectionId(Integer collectionId);

    Optional<CollectionItem> findByCollectionIdAndAiRecipeId(Integer collectionId, Integer aiRecipeId);

    boolean existsByCollectionIdAndAiRecipeId(Integer collectionId, Integer aiRecipeId);

    @Query("SELECT r.recipeId, r.name, r.imageUrl, r.createdAt, COUNT(c) "
            + "FROM CollectionItem c "
            + "JOIN Recipe r ON c.recipeId = r.recipeId "
            + // Join bảng Recipe để lấy thông tin
            "GROUP BY r.recipeId, r.name, r.imageUrl, r.createdAt "
            + "ORDER BY COUNT(c) DESC")
    List<Object[]> findMostFavoritedRecipes(Pageable pageable);
}
