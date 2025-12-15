package com.group02.zaderfood.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.*;
import org.hibernate.annotations.Nationalized;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "AiSavedRecipes")
public class AiSavedRecipes implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "AiRecipeId")
    private Integer aiRecipeId;

    @Column(name = "UserId")
    private Integer userId;

    @Column(name = "Name")
    @Nationalized
    private String name;

    @Column(name = "Description", columnDefinition = "NVARCHAR(MAX)")
    @Nationalized
    private String description;

    // Lưu danh sách dưới dạng văn bản (VD: "Trứng: 2 quả\nSữa: 100ml")
    @Column(name = "IngredientsText", columnDefinition = "NVARCHAR(MAX)")
    @Nationalized
    private String ingredientsText;

    @Column(name = "StepsText", columnDefinition = "NVARCHAR(MAX)")
    @Nationalized
    private String stepsText;

    @Column(name = "TimeMinutes")
    private Integer timeMinutes;

    @Column(name = "TotalCalories")
    private BigDecimal totalCalories;

    @Column(name = "Servings")
    private Integer servings;

    @Column(name = "ImageUrl")
    private String imageUrl;

    @Column(name = "SavedAt")
    private LocalDateTime savedAt;
    
    // Relation (Optional, nếu cần lấy thông tin User)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "UserId", insertable = false, updatable = false)
    private User user;
}