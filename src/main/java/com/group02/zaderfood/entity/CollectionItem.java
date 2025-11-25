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

    @Column(name = "AddedAt")
    private LocalDateTime addedAt;

    @Column(name = "CreatedAt")
    private LocalDateTime createdAt;

    @Column(name = "UpdatedAt")
    private LocalDateTime updatedAt;

    @Column(name = "IsDeleted")
    private Boolean isDeleted;

    @Column(name = "DeletedAt")
    private LocalDateTime deletedAt;

}
