package com.group02.zaderfood.controller;

import com.group02.zaderfood.entity.Recipe;
import com.group02.zaderfood.entity.RecipeCollection;
import com.group02.zaderfood.service.FavoriteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Controller
@RequestMapping("/collections")
public class PublicCollectionController {

    @Autowired
    private FavoriteService favoriteService;

    @GetMapping("/view/{id}")
    public String viewPublicCollection(@PathVariable("id") Integer collectionId, Model model) {
        Pair<RecipeCollection, List<Recipe>> data = favoriteService.getPublicCollectionData(collectionId);

        if (data == null) {
            return "error/404"; // Hoặc trang thông báo "Collection này là riêng tư"
        }

        model.addAttribute("collection", data.getFirst());
        model.addAttribute("recipes", data.getSecond());

        return "public-collection-view"; // File HTML mới
    }
}
