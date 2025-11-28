package com.group02.zaderfood.controller;

import com.group02.zaderfood.dto.IngredientInputDTO;
import com.group02.zaderfood.entity.IngredientCategory;
import com.group02.zaderfood.service.CustomUserDetails;
import com.group02.zaderfood.service.IngredientService;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin/ingredients")
public class AdminIngredientController {

    @Autowired
    private IngredientService ingredientService;

    // 1. Show the form to add a system ingredient
    @GetMapping("/create")
    public String showCreateIngredientForm(Model model) {
        // Reuse the logic to fetch categories
        List<IngredientCategory> categories = ingredientService.findAllCategories();
        model.addAttribute("categories", categories);
        
        // Use the same DTO for consistency
        model.addAttribute("newIngredient", new IngredientInputDTO());
        
        return "admin/addIngredient"; // We will create this view next
    }

    // 2. Handle the form submission
    @PostMapping("/create")
    public String createSystemIngredient(@ModelAttribute IngredientInputDTO form,
                                         @AuthenticationPrincipal CustomUserDetails currentUser) {
        
        // Call a service method to save the ingredient
        // Unlike the user request, this one should be ACTIVE immediately
        ingredientService.createSystemIngredient(form, currentUser.getUserId());
        
        return "redirect:/recipes/search?success=admin_added";
    }
}