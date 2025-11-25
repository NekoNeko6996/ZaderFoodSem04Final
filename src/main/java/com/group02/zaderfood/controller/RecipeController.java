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
        // Mặc định thêm 1 dòng trống để UI đẹp
        form.getIngredients().add(new IngredientInputDTO());
        
        model.addAttribute("recipeForm", form);
        // Load danh sách nguyên liệu có sẵn để user chọn (Dropdown/Datalist)
        model.addAttribute("availableIngredients", ingredientService.findAllActiveIngredients());
        // Load danh sách danh mục (Thịt, cá...) cho trường hợp tạo mới
        model.addAttribute("categories", ingredientService.findAllCategories());
        
        return "recipe/addRecipe";
    }

    @PostMapping("/create")
    public String createRecipe(@ModelAttribute RecipeCreationDTO form, 
                               @AuthenticationPrincipal CustomUserDetails currentUser) {
        // Logic xử lý lưu trữ (Sẽ giải thích kỹ ở phần Service sau nếu bạn cần)
        // 1. Duyệt list form.ingredients
        // 2. Nếu isNewIngredient == true -> Lưu vào bảng Ingredients (IsActive=false), lấy ID mới
        // 3. Lưu Recipe -> Lấy ID
        // 4. Lưu RecipeIngredients (Nối ID Recipe và ID Ingredient)
        
        recipeService.createFullRecipe(form, currentUser.getUserId());
        return "redirect:/recipes/my-recipes";
    }
    
    // 1. Trang danh sách tất cả công thức
    @GetMapping("/list")
    public String listRecipes(Model model) {
        // Lấy list recipe sắp xếp mới nhất lên đầu
        List<Recipe> recipes = recipeRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
        model.addAttribute("recipes", recipes);
        return "recipe/recipeList";
    }

    // 2. Trang xem chi tiết 1 công thức
    @GetMapping("/detail/{id}")
    public String viewRecipeDetail(@PathVariable Integer id, Model model) {
        Recipe recipe = recipeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy Recipe ID: " + id));
        
        model.addAttribute("recipe", recipe);
        return "recipe/recipeDetail";
    }
}