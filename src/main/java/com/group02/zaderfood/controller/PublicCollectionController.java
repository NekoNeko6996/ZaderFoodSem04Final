package com.group02.zaderfood.controller;

import com.group02.zaderfood.dto.UnifiedRecipeDTO; // <--- BẮT BUỘC PHẢI CÓ DÒNG NÀY
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
        
        // Hàm này trả về Pair<RecipeCollection, List<UnifiedRecipeDTO>>
        // Nên cần import UnifiedRecipeDTO để code biên dịch được
        Pair<RecipeCollection, List<UnifiedRecipeDTO>> data = favoriteService.getPublicCollectionData(collectionId);

        if (data == null) {
            return "error/404";
        }

        model.addAttribute("collection", data.getFirst());
        model.addAttribute("items", data.getSecond()); 

        return "public-collection-view";
    }
}