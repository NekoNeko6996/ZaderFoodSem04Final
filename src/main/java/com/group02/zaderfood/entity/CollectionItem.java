package com.group02.zaderfood.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "CollectionItems")
public class CollectionItem implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "CollectionItemId")
    private Integer collectionItemId;

    @Column(name = "CollectionId")
    private Integer collectionId;

    @Column(name = "RecipeId")
    private Integer recipeId;
    
    @Column(name = "AiRecipeId")
    private Integer aiRecipeId;

    @Column(name = "AddedAt")
    private LocalDateTime addedAt;
    
    // Relation (Optional - giúp truy vấn object dễ hơn nếu cần)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "AiRecipeId", insertable = false, updatable = false)
    private AiSavedRecipes aiSavedRecipe;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "RecipeId", insertable = false, updatable = false)
    private Recipe recipe;
}
