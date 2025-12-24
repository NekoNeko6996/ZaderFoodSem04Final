package com.group02.zaderfood.service;

import com.group02.zaderfood.dto.CollectionDTO;
import com.group02.zaderfood.dto.UnifiedRecipeDTO;
import com.group02.zaderfood.entity.AiSavedRecipes;
import com.group02.zaderfood.entity.CollectionItem;
import com.group02.zaderfood.entity.Recipe;
import com.group02.zaderfood.entity.RecipeCollection;
import com.group02.zaderfood.repository.AiSavedRecipeRepository;
import com.group02.zaderfood.repository.CollectionItemRepository;
import com.group02.zaderfood.repository.RecipeCollectionRepository;
import com.group02.zaderfood.repository.RecipeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.data.util.Pair;
import java.util.Objects;

@Service
public class FavoriteService {

    private static final String DEFAULT_COLLECTION_NAME = "Recipe Favorite";

    @Autowired
    private RecipeCollectionRepository collectionRepo;
    @Autowired
    private CollectionItemRepository collectionItemRepo;
    @Autowired
    private RecipeRepository recipeRepo;
    @Autowired
    private RecipeService recipeService; // Để tính Macro

    @Autowired
    private AiSavedRecipeRepository aiSavedRepo;

    // 1. Lấy danh sách Recipes (Có Search, Filter, Sort)
    @Transactional
    public List<Recipe> getFavoriteRecipes(Integer userId, String keyword, String sort) {
        Optional<RecipeCollection> favCollection = collectionRepo.findByUserIdAndName(userId, DEFAULT_COLLECTION_NAME);
        if (favCollection.isEmpty()) {
            return Collections.emptyList();
        }

        List<Integer> recipeIds = collectionItemRepo.findRecipeIdsByCollectionId(favCollection.get().getCollectionId());
        if (recipeIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<Recipe> recipes = recipeRepo.findAllById(recipeIds);

        // Tính Macro (giữ nguyên)
        for (Recipe r : recipes) {
            if (r != null && (r.getTotalCalories() == null || r.getTotalCalories().compareTo(BigDecimal.ZERO) == 0)) {
                recipeService.calculateRecipeMacros(r);
            }
        }

        return recipes.stream()
                .filter(Objects::nonNull) // <--- QUAN TRỌNG: Loại bỏ mọi recipe bị null
                .filter(r -> keyword == null || keyword.isEmpty()
                || (r.getName() != null && r.getName().toLowerCase().contains(keyword.toLowerCase())))
                .sorted((r1, r2) -> {
                    // Logic sort giữ nguyên
                    if ("time".equals(sort)) {
                        int t1 = (r1.getPrepTimeMin() == null ? 0 : r1.getPrepTimeMin()) + (r1.getCookTimeMin() == null ? 0 : r1.getCookTimeMin());
                        int t2 = (r2.getPrepTimeMin() == null ? 0 : r2.getPrepTimeMin()) + (r2.getCookTimeMin() == null ? 0 : r2.getCookTimeMin());
                        return Integer.compare(t1, t2);
                    } else if ("calories".equals(sort)) {
                        BigDecimal c1 = r1.getTotalCalories() == null ? BigDecimal.ZERO : r1.getTotalCalories();
                        BigDecimal c2 = r2.getTotalCalories() == null ? BigDecimal.ZERO : r2.getTotalCalories();
                        return c1.compareTo(c2);
                    }
                    // Sort ngày tạo (Đã fix null safe)
                    LocalDateTime d1 = r1.getCreatedAt();
                    LocalDateTime d2 = r2.getCreatedAt();
                    if (d1 == null && d2 == null) {
                        return 0;
                    }
                    if (d1 == null) {
                        return 1;
                    }
                    if (d2 == null) {
                        return -1;
                    }
                    return d2.compareTo(d1);
                })
                .collect(Collectors.toList());
    }

    // 2. Xóa món ăn khỏi danh sách yêu thích
    @Transactional
    public void removeFromFavorites(Integer userId, Integer recipeId) {
        Optional<RecipeCollection> favCollection = collectionRepo.findByUserIdAndName(userId, DEFAULT_COLLECTION_NAME);
        if (favCollection.isPresent()) {
            Integer colId = favCollection.get().getCollectionId();
            // Tìm item dựa trên CollectionId và RecipeId (Cần thêm hàm này trong Repo hoặc xử lý logic xóa)
            // Giả sử CollectionItemRepository có hàm deleteByCollectionIdAndRecipeId
            // Hoặc: Tìm CollectionItem rồi delete
            // Ở đây tôi giả định bạn sẽ dùng Query hoặc tìm list rồi xóa
            // Cách đơn giản nhất nếu chưa có hàm delete custom:
            CollectionItem item = collectionItemRepo.findAll().stream()
                    .filter(i -> i.getCollectionId().equals(colId) && i.getRecipeId().equals(recipeId))
                    .findFirst().orElse(null);

            if (item != null) {
                collectionItemRepo.delete(item);
            }
        }
    }

    // 3. Lấy danh sách các Collection của User (Trừ cái mặc định Recipe Favorite ra nếu muốn, hoặc lấy hết)
    public List<CollectionDTO> getUserCollectionsWithCount(Integer userId) {
        // Lấy tất cả collection của user
        List<RecipeCollection> collections = collectionRepo.findAll().stream()
                .filter(c -> c.getUserId().equals(userId))
                // Loại bỏ collection mặc định "Recipe Favorite" khỏi danh sách quản lý (nếu muốn)
                // .filter(c -> !c.getName().equals(DEFAULT_COLLECTION_NAME)) 
                .sorted(Comparator.comparing(RecipeCollection::getCreatedAt).reversed())
                .collect(Collectors.toList());

        // Map sang DTO kèm count
        return collections.stream().map(col -> {
            int count = collectionItemRepo.countByCollectionId(col.getCollectionId());
            return new CollectionDTO(col, count);
        }).collect(Collectors.toList());
    }

    @Transactional
    public Pair<RecipeCollection, List<UnifiedRecipeDTO>> getCollectionDetail(Integer userId, Integer collectionId) {

        Optional<RecipeCollection> colOpt = collectionRepo.findById(collectionId);

        if (colOpt.isPresent() && colOpt.get().getUserId().equals(userId)) {
            RecipeCollection col = colOpt.get();

            // 1. Lấy tất cả items trong collection (bao gồm cả AI và thường)
            List<CollectionItem> items = collectionItemRepo.findByCollectionId(collectionId);
            List<UnifiedRecipeDTO> resultList = new ArrayList<>();

            // 2. Lọc ID món thường và lấy dữ liệu
            List<Integer> standardIds = items.stream()
                    .map(CollectionItem::getRecipeId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            if (!standardIds.isEmpty()) {
                List<Recipe> recipes = recipeRepo.findAllById(standardIds);
                for (Recipe r : recipes) {
                    if (r.getTotalCalories() == null) {
                        recipeService.calculateRecipeMacros(r); // Fix calo null
                    }
                    int totalTime = (r.getPrepTimeMin() == null ? 0 : r.getPrepTimeMin())
                            + (r.getCookTimeMin() == null ? 0 : r.getCookTimeMin());

                    resultList.add(UnifiedRecipeDTO.builder()
                            .id(r.getRecipeId())
                            .name(r.getName())
                            .imageUrl(r.getImageUrl())
                            .calories(r.getTotalCalories())
                            .timeMin(totalTime)
                            .difficulty(r.getDifficulty() != null ? r.getDifficulty().name() : "EASY")
                            .isAi(false) // Đánh dấu là món thường
                            .build());
                }
            }

            // 3. Lọc ID món AI và lấy dữ liệu
            List<Integer> aiIds = items.stream()
                    .map(CollectionItem::getAiRecipeId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            if (!aiIds.isEmpty()) {
                List<AiSavedRecipes> aiRecipes = aiSavedRepo.findAllById(aiIds);
                for (AiSavedRecipes ar : aiRecipes) {
                    resultList.add(UnifiedRecipeDTO.builder()
                            .id(ar.getAiRecipeId())
                            .name(ar.getName())
                            .imageUrl(ar.getImageUrl())
                            .calories(ar.getTotalCalories())
                            .timeMin(ar.getTimeMinutes())
                            .difficulty("AI CHEF") // Badge riêng cho AI
                            .isAi(true) // Đánh dấu là AI
                            .build());
                }
            }

            return Pair.of(col, resultList);
        }
        return null;
    }

    @Transactional
    public void removeRecipeFromCollection(Integer userId, Integer collectionId, Integer recipeId, boolean isAi) {
        Optional<RecipeCollection> colOpt = collectionRepo.findById(collectionId);
        if (colOpt.isPresent() && colOpt.get().getUserId().equals(userId)) {
            CollectionItem item;
            if (isAi) {
                // Tìm theo AI ID
                // Bạn cần thêm hàm findByCollectionIdAndAiRecipeId vào Repo hoặc dùng stream lọc tay
                item = collectionItemRepo.findByCollectionId(collectionId).stream()
                        .filter(i -> i.getAiRecipeId() != null && i.getAiRecipeId().equals(recipeId))
                        .findFirst().orElse(null);
            } else {
                // Tìm theo Recipe ID thường
                item = collectionItemRepo.findByCollectionIdAndRecipeId(collectionId, recipeId)
                        .orElse(null);
            }

            if (item != null) {
                collectionItemRepo.delete(item);
            }
        }
    }

    // 4. Tạo Collection mới
    public void createCollection(Integer userId, String name) {
        RecipeCollection newCol = new RecipeCollection();
        newCol.setUserId(userId);
        newCol.setName(name);
        newCol.setIsPublic(false);
        newCol.setCreatedAt(LocalDateTime.now());
        newCol.setUpdatedAt(LocalDateTime.now());
        newCol.setIsDeleted(false);
        collectionRepo.save(newCol);
    }

    @Transactional
    public String toggleShareCollection(Integer userId, Integer collectionId) {
        Optional<RecipeCollection> colOpt = collectionRepo.findById(collectionId);

        if (colOpt.isPresent() && colOpt.get().getUserId().equals(userId)) {
            RecipeCollection col = colOpt.get();
            // Logic: Luôn set là true khi bấm Share (hoặc toggle tùy bạn)
            col.setIsPublic(true);
            col.setUpdatedAt(LocalDateTime.now());
            collectionRepo.save(col);
            return "SUCCESS";
        }
        return "ERROR";
    }

    // 2. Hàm lấy dữ liệu cho trang Public (Dành cho người xem)
    @Transactional(readOnly = true)
    public Pair<RecipeCollection, List<UnifiedRecipeDTO>> getPublicCollectionData(Integer collectionId) {

        // 1. Tìm Collection
        RecipeCollection col = collectionRepo.findById(collectionId).orElse(null);
        if (col == null || !col.getIsPublic()) {
            return null;
        }

        // 2. Lấy list items
        List<CollectionItem> items = collectionItemRepo.findByCollectionId(collectionId);
        List<UnifiedRecipeDTO> unifiedList = new ArrayList<>();

        for (CollectionItem item : items) {
            // Sử dụng Builder
            UnifiedRecipeDTO.UnifiedRecipeDTOBuilder builder = UnifiedRecipeDTO.builder();
            boolean hasData = false;

            // CASE A: Món thường (Standard Recipe)
            if (item.getRecipe() != null) {
                Recipe r = item.getRecipe();
                int totalTime = (r.getPrepTimeMin() != null ? r.getPrepTimeMin() : 0)
                        + (r.getCookTimeMin() != null ? r.getCookTimeMin() : 0);

                builder.id(r.getRecipeId())
                        .name(r.getName())
                        .imageUrl(r.getImageUrl())
                        .calories(r.getTotalCalories())
                        .timeMin(totalTime)
                        .isAi(false)
                        .difficulty(r.getDifficulty() != null ? r.getDifficulty().toString() : "Medium");

                hasData = true;
            } // CASE B: Món AI (AI Recipe)
            else if (item.getAiSavedRecipe() != null) {
                AiSavedRecipes ai = item.getAiSavedRecipe();

                builder.id(ai.getAiRecipeId())
                        .name(ai.getName())
                        .imageUrl(ai.getImageUrl())
                        .calories(ai.getTotalCalories())
                        .timeMin(ai.getTimeMinutes())
                        .isAi(true)
                        .difficulty("AI Generated"); // Giá trị mặc định cho AI

                hasData = true;
            }

            if (hasData) {
                unifiedList.add(builder.build());
            }
        }

        return Pair.of(col, unifiedList);
    }

    @Transactional // Quan trọng: Để đảm bảo tính toàn vẹn dữ liệu
    public void deleteCollection(Integer userId, Integer collectionId) {
        // 1. Tìm Collection
        Optional<RecipeCollection> colOpt = collectionRepo.findById(collectionId);

        // 2. Kiểm tra quyền sở hữu
        if (colOpt.isPresent() && colOpt.get().getUserId().equals(userId)) {
            RecipeCollection col = colOpt.get();

            // 3. Chặn xóa collection mặc định (An toàn)
            if ("Recipe Favorite".equals(col.getName())) {
                throw new RuntimeException("Cannot delete default collection");
            }

            // 4. Bước 1: Xóa sạch các món ăn trong Collection này trước
            // Lệnh này sẽ xóa vĩnh viễn trong bảng CollectionItems
            collectionItemRepo.deleteByCollectionId(collectionId);

            // 5. Bước 2: Xóa vĩnh viễn Collection
            collectionRepo.delete(col);
        } else {
            // (Tùy chọn) Ném lỗi nếu không tìm thấy hoặc không phải chủ sở hữu
            // throw new RuntimeException("Collection not found or access denied");
        }
    }
}
