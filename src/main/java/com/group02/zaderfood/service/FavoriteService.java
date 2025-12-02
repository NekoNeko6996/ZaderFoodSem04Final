package com.group02.zaderfood.service;

import com.group02.zaderfood.dto.CollectionDTO;
import com.group02.zaderfood.entity.CollectionItem;
import com.group02.zaderfood.entity.Recipe;
import com.group02.zaderfood.entity.RecipeCollection;
import com.group02.zaderfood.repository.CollectionItemRepository;
import com.group02.zaderfood.repository.RecipeCollectionRepository;
import com.group02.zaderfood.repository.RecipeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
    public Pair<RecipeCollection, List<Recipe>> getCollectionDetail(Integer userId, Integer collectionId) {
        // Tìm Collection và đảm bảo nó thuộc về User này
        Optional<RecipeCollection> colOpt = collectionRepo.findById(collectionId);

        if (colOpt.isPresent() && colOpt.get().getUserId().equals(userId)) {
            RecipeCollection col = colOpt.get();

            // Lấy danh sách Recipe
            List<Integer> recipeIds = collectionItemRepo.findRecipeIdsByCollectionId(collectionId);
            List<Recipe> recipes = recipeRepo.findAllById(recipeIds);

            // Tính Macro cho hiển thị đẹp
            for (Recipe r : recipes) {
                if (r.getTotalCalories() == null || r.getTotalCalories().compareTo(BigDecimal.ZERO) == 0) {
                    recipeService.calculateRecipeMacros(r);
                }
            }

            return Pair.of(col, recipes); // Cần import org.springframework.data.util.Pair hoặc dùng Map
        }
        return null;
    }

    @Transactional
    public void removeRecipeFromCollection(Integer userId, Integer collectionId, Integer recipeId) {
        // Kiểm tra quyền sở hữu collection
        Optional<RecipeCollection> colOpt = collectionRepo.findById(collectionId);
        if (colOpt.isPresent() && colOpt.get().getUserId().equals(userId)) {
            // Tìm item và xóa
            Optional<CollectionItem> item = collectionItemRepo.findByCollectionIdAndRecipeId(collectionId, recipeId);
            item.ifPresent(collectionItem -> collectionItemRepo.delete(collectionItem));
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
    public Pair<RecipeCollection, List<Recipe>> getPublicCollectionData(Integer collectionId) {
        Optional<RecipeCollection> colOpt = collectionRepo.findById(collectionId);

        // QUAN TRỌNG: Chỉ trả về nếu IsPublic = true
        if (colOpt.isPresent() && Boolean.TRUE.equals(colOpt.get().getIsPublic())) {
            List<Integer> recipeIds = collectionItemRepo.findRecipeIdsByCollectionId(collectionId);
            List<Recipe> recipes = recipeRepo.findAllById(recipeIds);

            // Tính macro để hiển thị đẹp
            for (Recipe r : recipes) {
                if (r.getTotalCalories() == null || r.getTotalCalories().compareTo(BigDecimal.ZERO) == 0) {
                    recipeService.calculateRecipeMacros(r);
                }
            }
            return Pair.of(colOpt.get(), recipes);
        }
        return null; // Không tìm thấy hoặc chưa public
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
