package com.group02.zaderfood.controller;

import com.group02.zaderfood.dto.IngredientInputDTO;
import com.group02.zaderfood.dto.RecipeCreationDTO;
import com.group02.zaderfood.entity.Recipe;
import com.group02.zaderfood.repository.RecipeRepository;
import com.group02.zaderfood.service.CustomUserDetails;
import com.group02.zaderfood.service.IngredientService;
import com.group02.zaderfood.service.RecipeService;
import java.util.List;
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


@Controller
@RequestMapping("/recipes")
public class RecipeController {

    @Autowired private IngredientService ingredientService;
    @Autowired private RecipeService recipeService;
    @Autowired private RecipeRepository recipeRepository;

    @GetMapping("/create")
    public String showCreateForm(Model model) {
        RecipeCreationDTO form = new RecipeCreationDTO();
        form.getIngredients().add(new IngredientInputDTO());
        model.addAttribute("recipeForm", form);
        model.addAttribute("availableIngredients", ingredientService.findAllActiveIngredients());
        model.addAttribute("categories", ingredientService.findAllCategories());
        
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
}