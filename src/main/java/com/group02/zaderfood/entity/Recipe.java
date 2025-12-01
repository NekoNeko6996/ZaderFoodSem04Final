package com.group02.zaderfood.entity;

import com.group02.zaderfood.entity.enums.DifficultyLevel;
import com.group02.zaderfood.entity.enums.RecipeStatus;
import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "Recipes")
public class Recipe implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "RecipeId")
    private Integer recipeId;

    @Column(name = "Name")
    private String name;

    @Column(name = "Description")
    private String description;

    @Column(name = "Difficulty")
    @Enumerated(EnumType.STRING)
    private DifficultyLevel difficulty;

    @Column(name = "PrepTimeMin")
    private Integer prepTimeMin;

    @Column(name = "CookTimeMin")
    private Integer cookTimeMin;

    @Column(name = "Servings")
    private Integer servings;

    @Column(name = "TotalCalories")
    private BigDecimal totalCalories;

    @Column(name = "ImageUrl")
    private String imageUrl;

    @Column(name = "Status")
    @Enumerated(EnumType.STRING)
    private RecipeStatus status;

    @Column(name = "CreatedByUserId")
    private Integer createdByUserId;

    @Column(name = "CreatedAt")
    private LocalDateTime createdAt;

    @Column(name = "UpdatedAt")
    private LocalDateTime updatedAt;

    @Column(name = "IsDeleted")
    private Boolean isDeleted;

    @Column(name = "DeletedAt")
    private LocalDateTime deletedAt;
    
    //
    @Transient
    private BigDecimal protein;

    @Transient
    private BigDecimal carbs;

    @Transient
    private BigDecimal fat;

    // JOIN
    @OneToMany(mappedBy = "recipe")
    private List<RecipeIngredient> recipeIngredients;

    @OneToMany(mappedBy = "recipe")
    private List<RecipeStep> recipeSteps;

    @ManyToOne
    @JoinColumn(name = "CreatedByUserId", insertable = false, updatable = false)
    private User user;
}
