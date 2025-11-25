package com.group02.zaderfood.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "RecipeIngredients")
public class RecipeIngredient implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "RecipeIngredientId")
    private Integer recipeIngredientId;

    @Column(name = "RecipeId")
    private Integer recipeId;

    @Column(name = "IngredientId")
    private Integer ingredientId;

    @Column(name = "Quantity")
    private BigDecimal quantity;

    @Column(name = "Unit")
    private String unit;

    @Column(name = "Note")
    private String note;

    @Column(name = "CreatedAt")
    private LocalDateTime createdAt;

    @Column(name = "UpdatedAt")
    private LocalDateTime updatedAt;

    @Column(name = "IsDeleted")
    private Boolean isDeleted;

    @Column(name = "DeletedAt")
    private LocalDateTime deletedAt;

    // JOIN 
    @ManyToOne
    @JoinColumn(name = "IngredientId", insertable = false, updatable = false)
    private Ingredient ingredient;
    
    @ManyToOne
    @JoinColumn(name = "RecipeId", insertable = false, updatable = false)
    private Recipe recipe;
}
