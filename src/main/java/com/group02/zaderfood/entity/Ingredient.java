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
@Table(name = "Ingredients")
public class Ingredient implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "IngredientId")
    private Integer ingredientId;

    @Column(name = "Name")
    private String name;

    @Column(name = "CaloriesPer100g")
    private BigDecimal caloriesPer100g;

    @Column(name = "Protein")
    private BigDecimal protein;

    @Column(name = "Carbs")
    private BigDecimal carbs;

    @Column(name = "Fat")
    private BigDecimal fat;

    @Column(name = "BaseUnit")
    private String baseUnit;

    @Column(name = "ImageUrl")
    private String imageUrl;

    @Column(name = "CategoryId")
    private Integer categoryId;

    @Column(name = "IsActive")
    private Boolean isActive;

    @Column(name = "CreatedAt")
    private LocalDateTime createdAt;

    @Column(name = "UpdatedAt")
    private LocalDateTime updatedAt;

    @Column(name = "IsDeleted")
    private Boolean isDeleted;

    @Column(name = "DeletedAt")
    private LocalDateTime deletedAt;

}
