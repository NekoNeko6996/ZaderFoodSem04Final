package com.group02.zaderfood.controller;

import com.group02.zaderfood.entity.Recipe;
import com.group02.zaderfood.service.RecipeService;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @Autowired
    private RecipeService recipeService;

    @GetMapping("/") // Hoặc @GetMapping("/home") tùy route của bạn
    public String home(Model model) {
        // Lấy 8 công thức mới nhất
        List<Recipe> newRecipes = recipeService.getLatestRecipes(8);
        model.addAttribute("newRecipes", newRecipes);
        
        return "home";
    }
}