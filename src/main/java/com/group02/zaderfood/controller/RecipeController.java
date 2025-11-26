package com.group02.zaderfood.controller;

import com.group02.zaderfood.dto.IngredientInputDTO;
import com.group02.zaderfood.dto.RecipeCreationDTO;
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
    public String suggestionsPage(@RequestParam(name = "ids") List<Integer> ingredientIds, Model model) {
        List<Ingredient> selectedIngredients = ingredientRepository.findAllById(ingredientIds);
        model.addAttribute("selectedIngredients", selectedIngredients);
        List<Recipe> matchedRecipes = recipeService.findRecipesByIngredientIds(ingredientIds);
        model.addAttribute("recipes", matchedRecipes);
        return "recipe/results";
    }
}
