package com.group02.zaderfood.controller;

import com.group02.zaderfood.dto.IngredientInputDTO;
import com.group02.zaderfood.dto.RecipeCreationDTO;
import com.group02.zaderfood.dto.RecipeMatchDTO;
import com.group02.zaderfood.entity.Ingredient;
import com.group02.zaderfood.entity.Recipe;
import com.group02.zaderfood.repository.IngredientRepository;
import com.group02.zaderfood.repository.RecipeRepository;
import com.group02.zaderfood.service.CustomUserDetails;
import com.group02.zaderfood.service.IngredientService;
import com.group02.zaderfood.service.RecipeService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
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
        recipeService.createFullRecipe(form, currentUser.getUserId());
        return "redirect:/recipes/my-recipes";
    }

    @GetMapping("/list")
    public String listRecipes(Model model) {
        List<Recipe> recipes = recipeRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
        model.addAttribute("recipes", recipes);
        return "recipe/recipeList";
    }

    @GetMapping("/detail/{id}")
    public String viewRecipeDetail(@PathVariable Integer id, Model model) {
        Recipe recipe = recipeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy Recipe ID: " + id));

        model.addAttribute("recipe", recipe);
        return "recipe/recipeDetail";
    }

    @GetMapping("/search")
    public String searchPage(@RequestParam(name = "ids", required = false) List<Integer> ids, Model model) {
        List<Ingredient> allIngredients = ingredientRepository.findAll();

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
            Model model) {

        // 1. Sidebar Selected Ingredients
        if (ingredientIds != null && !ingredientIds.isEmpty()) {
            model.addAttribute("selectedIngredients", ingredientRepository.findAllById(ingredientIds));
        } else {
            model.addAttribute("selectedIngredients", new ArrayList<>());
        }

        // 2. Gọi Service mới trả về List<RecipeMatchDTO>
        List<RecipeMatchDTO> matchResults = recipeService.findRecipesWithMissingIngredients(ingredientIds, keyword, maxCalories, maxTime, difficulty);

        model.addAttribute("recipeMatches", matchResults); // Đổi tên biến model để phân biệt

        // 3. Giữ trạng thái filter
        model.addAttribute("keyword", keyword);
        model.addAttribute("maxCalories", maxCalories);
        model.addAttribute("maxTime", maxTime);
        model.addAttribute("difficulty", difficulty);

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
}
