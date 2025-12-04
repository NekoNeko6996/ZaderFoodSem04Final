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
@Table(name = "UserFavoriteIngredients")
public class UserFavoriteIngredient implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "FavoriteId")
    private Integer favoriteId;

    @Column(name = "UserId")
    private Integer userId;

    @Column(name = "IngredientId")
    private Integer ingredientId;

    @Column(name = "AddedAt")
    private LocalDateTime addedAt;

    // Các cột Audit (CreatedAt, UpdatedAt, IsDeleted...) 
    // KHÔNG CÓ trong script v2.0 của bảng này nên đã bị xóa đi.
}
