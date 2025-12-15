package com.group02.zaderfood.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.*;
import org.hibernate.annotations.Nationalized;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "UserPantry")
public class UserPantry implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "PantryId")
    private Integer pantryId;

    @Column(name = "UserId")
    private Integer userId;

    @Column(name = "IngredientId")
    private Integer ingredientId;

    @Column(name = "Quantity")
    private BigDecimal quantity;

    @Column(name = "Unit")
    @Nationalized
    private String unit;

    @Column(name = "ExpiryDate")
    private LocalDate expiryDate; // SQL Type: DATE

    @Column(name = "CreatedAt")
    private LocalDateTime createdAt;
    
    // Relations
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "IngredientId", insertable = false, updatable = false)
    private Ingredient ingredient;
}