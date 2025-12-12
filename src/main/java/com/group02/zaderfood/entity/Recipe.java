package com.group02.zaderfood.entity;

import com.group02.zaderfood.entity.enums.DifficultyLevel;
import com.group02.zaderfood.entity.enums.RecipeStatus;
import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.*;
import org.hibernate.annotations.Nationalized;
import org.springframework.web.multipart.MultipartFile;

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
    @Nationalized
    private String name;

    @Column(name = "Description", columnDefinition = "NVARCHAR(MAX)")
    private String description;

    @Column(name = "Difficulty")
    @Nationalized
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
    
    @Column(name = "IsNutritionist")
    private boolean isNutritionist;

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
    
    @Transient
    private MultipartFile imageFile;

    // JOIN
    @OneToMany(mappedBy = "recipe")
    private List<RecipeIngredient> recipeIngredients;

    @OneToMany(mappedBy = "recipe")
    private List<RecipeStep> recipeSteps;

    @ManyToOne
    @JoinColumn(name = "CreatedByUserId", insertable = false, updatable = false)
    private User user;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "RecipeId", insertable = false, updatable = false)
    @ToString.Exclude
    private Recipe recipe;
}
