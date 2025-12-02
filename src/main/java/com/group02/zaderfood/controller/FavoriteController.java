package com.group02.zaderfood.controller;

import com.group02.zaderfood.entity.Recipe;
import com.group02.zaderfood.entity.RecipeCollection;
import com.group02.zaderfood.service.CustomUserDetails;
import com.group02.zaderfood.service.FavoriteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import com.group02.zaderfood.dto.CollectionDTO;
import org.springframework.data.util.Pair;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/user/favorites")
public class FavoriteController {

    @Autowired
    private FavoriteService favoriteService;

    @GetMapping
    public String viewFavorites(Model model,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(name = "view", defaultValue = "recipes") String view,
            @RequestParam(name = "collectionId", required = false) Integer collectionId,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "sort", required = false) String sort) {

        if (userDetails == null) {
            return "redirect:/login";
        }
        Integer userId = userDetails.getUserId();

        // VIEW 1: FAVORITE RECIPES
        if ("recipes".equals(view)) {
            List<Recipe> favoriteRecipes = favoriteService.getFavoriteRecipes(userId, keyword, sort);
            model.addAttribute("favoriteRecipes", favoriteRecipes);
        } // VIEW 2: LIST COLLECTIONS
        else if ("collections".equals(view)) {
            List<CollectionDTO> myCollections = favoriteService.getUserCollectionsWithCount(userId);
            model.addAttribute("myCollections", myCollections);
        } // VIEW 3: COLLECTION DETAIL (Dynamic Tab)
        else if ("detail".equals(view) && collectionId != null) {
            Pair<RecipeCollection, List<Recipe>> result = favoriteService.getCollectionDetail(userId, collectionId);
            if (result != null) {
                model.addAttribute("selectedCollection", result.getFirst());
                model.addAttribute("collectionRecipes", result.getSecond());
            } else {
                return "redirect:/user/favorites?view=collections"; // Không tìm thấy hoặc không có quyền
            }
        }

        model.addAttribute("activeTab", view);
        return "user/my-favorite-collection";
    }

    // Action: Tạo Collection (Có Toast)
    @PostMapping("/collections/create")
    public String createCollection(@AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam("name") String name,
            RedirectAttributes redirectAttributes) {
        if (userDetails != null && name != null && !name.trim().isEmpty()) {
            favoriteService.createCollection(userDetails.getUserId(), name.trim());
            redirectAttributes.addFlashAttribute("successMessage", "Collection created successfully!");
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", "Invalid collection name!");
        }
        return "redirect:/user/favorites?view=collections";
    }

    // Action: Xóa Recipe khỏi Collection cụ thể (Có Toast)
    @PostMapping("/collections/remove-item")
    public String removeItemFromCollection(@AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam("collectionId") Integer collectionId,
            @RequestParam("recipeId") Integer recipeId,
            RedirectAttributes redirectAttributes) {
        if (userDetails != null) {
            favoriteService.removeRecipeFromCollection(userDetails.getUserId(), collectionId, recipeId);
            redirectAttributes.addFlashAttribute("successMessage", "Recipe removed from collection.");
        }
        return "redirect:/user/favorites?view=detail&collectionId=" + collectionId;
    }

    // Action: Xóa Recipe khỏi Favorites gốc (Giữ nguyên logic cũ, thêm toast)
    @PostMapping("/remove")
    public String removeRecipe(@AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam("recipeId") Integer recipeId,
            RedirectAttributes redirectAttributes) {
        if (userDetails != null) {
            favoriteService.removeFromFavorites(userDetails.getUserId(), recipeId);
            redirectAttributes.addFlashAttribute("successMessage", "Removed from favorites.");
        }
        return "redirect:/user/favorites?view=recipes";
    }
    
    @PostMapping("/collections/share")
    @ResponseBody // Trả về JSON/String thay vì View
    public ResponseEntity<?> shareCollection(@AuthenticationPrincipal CustomUserDetails userDetails,
                                             @RequestParam("collectionId") Integer collectionId) {
        if (userDetails != null) {
            String result = favoriteService.toggleShareCollection(userDetails.getUserId(), collectionId);
            if ("SUCCESS".equals(result)) {
                // Trả về URL đầy đủ để frontend copy
                String shareUrl = "/collections/view/" + collectionId; 
                return ResponseEntity.ok(shareUrl);
            }
        }
        return ResponseEntity.badRequest().body("Error sharing collection");
    }
    
    @PostMapping("/collections/delete")
    public String deleteCollection(@AuthenticationPrincipal CustomUserDetails userDetails,
                                   @RequestParam("collectionId") Integer collectionId,
                                   RedirectAttributes redirectAttributes) {
        if (userDetails != null) {
            try {
                favoriteService.deleteCollection(userDetails.getUserId(), collectionId);
                redirectAttributes.addFlashAttribute("successMessage", "Collection deleted successfully.");
            } catch (Exception e) {
                redirectAttributes.addFlashAttribute("errorMessage", "Could not delete collection.");
            }
        }
        return "redirect:/user/favorites?view=collections";
    }
}
