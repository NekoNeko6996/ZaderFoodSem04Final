package com.group02.zaderfood.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import lombok.*;
import org.hibernate.annotations.Nationalized;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "ShoppingListItems")
public class ShoppingListItem implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ItemId")
    private Integer itemId;

    @Column(name = "ListId")
    private Integer listId;

    @Column(name = "IngredientId")
    private Integer ingredientId; // Có thể null nếu là món custom

    @Column(name = "CustomItemName")
    @Nationalized
    private String customItemName;

    @Column(name = "Quantity")
    private BigDecimal quantity;

    @Column(name = "Unit")
    @Nationalized
    private String unit;

    @Column(name = "IsBought")
    private Boolean isBought; // Checkbox (0: Chưa mua, 1: Đã mua)
}