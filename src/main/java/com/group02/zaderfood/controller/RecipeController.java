package com.group02.zaderfood.controller;

import com.group02.zaderfood.dto.IngredientInputDTO;
import com.group02.zaderfood.dto.RecipeCreationDTO;
import com.group02.zaderfood.dto.RecipeMatchDTO;
import com.group02.zaderfood.entity.Ingredient;
import com.group02.zaderfood.entity.Recipe;
import com.group02.zaderfood.entity.Review;
import com.group02.zaderfood.entity.enums.RecipeStatus;
import com.group02.zaderfood.entity.enums.UserRole;
import com.group02.zaderfood.repository.IngredientRepository;
import com.group02.zaderfood.repository.RecipeRepository;
import com.group02.zaderfood.repository.ReviewRepository;
import com.group02.zaderfood.service.CustomUserDetails;
import com.group02.zaderfood.service.IngredientService;
import com.group02.zaderfood.service.RecipeService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping("/recipes")
public class RecipeController {

    @Autowired
    private IngredientService ingredientService;
    @Autowired
    private RecipeService recipeService;
    @Autowired
    private RecipeRepository recipeRepository;
    @Autowired
    private IngredientRepository ingredientRepository;
    @Autowired
    private ReviewRepository reviewRepository;

    @GetMapping("/create")
    public String showCreateForm(Model model) {
        RecipeCreationDTO form = new RecipeCreationDTO();
        form.getIngredients().add(new IngredientInputDTO());
        model.addAttribute("recipeForm", form);

        // category
        List<Map<String, Object>> simpleCategories = ingredientService.findAllCategories().stream()
                .map(cat -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("categoryId", cat.getCategoryId()); // Ensure this getter exists
                    map.put("name", cat.getName());             // Ensure this getter exists
                    return map;
                })
                .collect(Collectors.toList());

        model.addAttribute("categories", simpleCategories);
        // ---------------------------

        model.addAttribute("availableIngredients", ingredientService.findAllActiveIngredients());

        return "recipe/addRecipe";
    }

    @PostMapping("/create")
    public String createRecipe(@ModelAttribute RecipeCreationDTO form,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        /* ----------------------------------------
         * Storage processing logic
         * 1. Browse the form.ingredients list
         * 2. If isNewIngredient == true -> Save to the Ingredients table (IsActive=false), get the new ID
         * 3. Save Recipe -> Get ID
         * 4. Save RecipeIngredients (Connect Recipe ID and Ingredient ID)
         */
        recipeService.createFullRecipe(form, currentUser.getUserId(), currentUser.getUserRole() == UserRole.NUTRITIONIST);
        return "redirect:/recipes/thank-you";
    }

    @GetMapping("/detail/{id}")
    public String viewRecipeDetail(@PathVariable Integer id, Model model) {
        Recipe recipe = recipeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy Recipe ID: " + id));

        List<Review> reviews = reviewRepository.findByRecipeIdOrderByCreatedAtDesc(id);

        // Tính điểm trung bình (nếu thích hiển thị số sao trung bình)
        double averageRating = reviews.stream()
                .mapToInt(Review::getRating)
                .average()
                .orElse(0.0);

        boolean isActive = recipe.getStatus().equals(RecipeStatus.ACTIVE);
        
        recipeService.calculateRecipeMacros(recipe);
        
        model.addAttribute("isActive", isActive);
        model.addAttribute("recipe", recipe);
        model.addAttribute("reviews", reviews); // Danh sách comment
        model.addAttribute("averageRating", String.format("%.1f", averageRating)); // Điểm TB
        model.addAttribute("totalReviews", reviews.size()); // Tổng số đánh giá
        model.addAttribute("newReview", new Review()); // Object cho form
        model.addAttribute("recipe", recipe);

        return "recipe/recipeDetail";
    }

    @PostMapping("/detail/{id}/review")
    public String submitReview(@PathVariable Integer id,
            @ModelAttribute("newReview") Review review,
            @AuthenticationPrincipal CustomUserDetails currentUser) {

        if (currentUser == null) {
            return "redirect:/login"; // Bắt buộc login mới được review
        }

        // Set các thông tin cần thiết
        review.setRecipeId(id);
        review.setUserId(currentUser.getUserId());
        review.setCreatedAt(LocalDateTime.now());

        // Lưu vào DB
        reviewRepository.save(review);

        // Redirect lại trang chi tiết để thấy comment vừa đăng
        return "redirect:/recipes/detail/" + id;
    }
    
    @PostMapping("/detail/{recipeId}/review/{reviewId}/delete")
    public String deleteReview(@PathVariable Integer recipeId,
                               @PathVariable Integer reviewId,
                               @AuthenticationPrincipal CustomUserDetails currentUser) {
        
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("Review not found"));

        // Bảo mật: Chỉ cho phép xóa nếu là chủ sở hữu hoặc là Admin
        if (currentUser != null && (review.getUserId().equals(currentUser.getUserId()) 
                                    || currentUser.getUserRole() == UserRole.ADMIN)) {
            reviewRepository.delete(review);
        }

        return "redirect:/recipes/detail/" + recipeId;
    }
    
    @PostMapping("/detail/{recipeId}/review/{reviewId}/edit")
    public String updateReview(@PathVariable Integer recipeId,
                               @PathVariable Integer reviewId,
                               @RequestParam Integer rating,
                               @RequestParam String comment,
                               @AuthenticationPrincipal CustomUserDetails currentUser) {
        
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("Review not found"));

        // Bảo mật: Chỉ chủ sở hữu mới được sửa
        if (currentUser != null && review.getUserId().equals(currentUser.getUserId())) {
            review.setRating(rating);
            review.setComment(comment);
            review.setCreatedAt(LocalDateTime.now());
            reviewRepository.save(review);
        }

        return "redirect:/recipes/detail/" + recipeId;
    }

    @GetMapping("/search")
    public String searchPage(@RequestParam(name = "ids", required = false) List<Integer> ids, Model model) {
        List<Ingredient> allIngredients = ingredientRepository.findByIsActiveTrue();

        Map<String, List<Ingredient>> ingredientsByCategory = allIngredients.stream()
                .collect(Collectors.groupingBy(ing -> {
                    if (ing.getIngredientCategory() != null) {
                        return ing.getIngredientCategory().getName();
                    }
                    return "Others";
                }));

        model.addAttribute("categories", ingredientsByCategory);
        model.addAttribute("preSelectedIds", ids != null ? ids : new ArrayList<>());

        return "recipe/search";
    }

    @GetMapping("/suggestions")
    public String suggestionsPage(
            @RequestParam(name = "ids", required = false) List<Integer> ingredientIds,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "maxCalories", required = false) Integer maxCalories,
            @RequestParam(name = "maxTime", required = false) Integer maxTime,
            @RequestParam(name = "difficulty", required = false) String difficulty,
            @RequestParam(name = "isNutritionist", required = false) Boolean isNutritionist,
            Model model) {

        // 1. Sidebar Selected Ingredients
        if (ingredientIds != null && !ingredientIds.isEmpty()) {
            model.addAttribute("selectedIngredients", ingredientRepository.findAllById(ingredientIds));
        } else {
            model.addAttribute("selectedIngredients", new ArrayList<>());
        }

        // 2. Gọi Service mới trả về List<RecipeMatchDTO>
        List<RecipeMatchDTO> matchResults = recipeService.findRecipesWithMissingIngredients(
                ingredientIds, keyword, maxCalories, maxTime, difficulty, isNutritionist
        );

        model.addAttribute("recipeMatches", matchResults); // Đổi tên biến model để phân biệt

        // 3. Giữ trạng thái filter
        model.addAttribute("keyword", keyword);
        model.addAttribute("maxCalories", maxCalories);
        model.addAttribute("maxTime", maxTime);
        model.addAttribute("difficulty", difficulty);
        model.addAttribute("isNutritionist", isNutritionist);

        return "recipe/results";
    }

    @PostMapping("/api/favorite/{recipeId}")
    @ResponseBody
    public ResponseEntity<?> addToFavorite(@PathVariable Integer recipeId,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        if (currentUser == null) {
            return ResponseEntity.status(401).body("User not logged in");
        }

        boolean added = recipeService.toggleFavorite(currentUser.getUserId(), recipeId);

        if (added) {
            return ResponseEntity.ok().body(Map.of("message", "Added to favorites", "status", "added"));
        } else {
            return ResponseEntity.ok().body(Map.of("message", "Already in favorites", "status", "exists"));
        }
    }

    @GetMapping("/request-ingredient")
    public String requestIngredientPage(Model model) {
        // Lấy danh sách category để user chọn
        model.addAttribute("categories", ingredientService.findAllCategories());

        // Dùng lại DTO IngredientInputDTO hoặc tạo DTO mới tùy bạn
        model.addAttribute("newIngredient", new IngredientInputDTO());

        return "recipe/requestIngredient"; // Trả về file HTML form
    }

    // 2. Xử lý khi user nhấn Submit Form
    @PostMapping("/request-ingredient")
    public String submitIngredientRequest(@ModelAttribute IngredientInputDTO form,
            @AuthenticationPrincipal CustomUserDetails currentUser) {

        if (currentUser == null) {
            return "redirect:/login";
        }

        // Gọi Service để lưu nguyên liệu mới (Trạng thái chờ duyệt)
        ingredientService.requestNewIngredient(form, currentUser.getUserId());

        // Quay lại trang Search và báo thành công
        return "redirect:/recipes/search?success=request_submitted";
    }

    @GetMapping("/all-mini")
    public ResponseEntity<?> getAllRecipesMini() {
        // Lấy toàn bộ món ăn (hoặc giới hạn 500 món nếu DB quá lớn sau này)
        // Lưu ý: Chỉ lấy các trường cần thiết để JSON nhẹ nhất có thể
        List<Recipe> recipes = recipeRepository.findAll();

        List<Map<String, Object>> result = recipes.stream().map(r -> {
            Map<String, Object> map = new java.util.HashMap<>();
            map.put("recipeId", r.getRecipeId());
            map.put("name", r.getName()); // Tên để search
            map.put("calories", r.getTotalCalories()); // Calo để lọc
            map.put("image", r.getImageUrl() != null ? r.getImageUrl() : "/images/default-food.png");

            // Thêm trường tìm kiếm không dấu (optional - để tìm 'pho' ra 'phở')
            // map.put("searchString", removeAccents(r.getName()).toLowerCase()); 
            return map;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    @GetMapping("/thank-you")
    public String showThankYouPage() {
        return "recipe-thank-you";
    }
    
    @GetMapping("/my-recipes")
    public String viewMyRecipes(Model model,
                                @AuthenticationPrincipal CustomUserDetails user,
                                @RequestParam(required = false) String keyword,
                                @RequestParam(required = false) RecipeStatus status) {
        
        if (user == null) return "redirect:/login";

        // 1. Lấy danh sách món ăn của user
        List<Recipe> myRecipes = recipeService.getMyRecipes(user.getUserId(), keyword, status);
        
        // 2. Đẩy dữ liệu ra view
        model.addAttribute("recipes", myRecipes);
        
        
        // 3. Giữ lại trạng thái filter để hiển thị trên UI
        model.addAttribute("keyword", keyword);
        model.addAttribute("currentStatus", status);
        model.addAttribute("allStatuses", RecipeStatus.values()); // Để đổ vào dropdown

        return "recipe/my-recipes"; // Tên file HTML sẽ tạo
    }
}
