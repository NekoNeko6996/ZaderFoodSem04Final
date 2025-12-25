package com.group02.zaderfood.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.*;
import org.hibernate.annotations.Nationalized;
import org.hibernate.annotations.SQLRestriction;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "IngredientCategories")
@SQLRestriction("IsDeleted = 0")
public class IngredientCategory implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "CategoryId")
    private Integer categoryId;

    @Column(name = "Name")
    @Nationalized
    private String name;

    @Column(name = "CreatedAt")
    private LocalDateTime createdAt;

    @Column(name = "UpdatedAt")
    private LocalDateTime updatedAt;

    @Column(name = "IsDeleted")
    private Boolean isDeleted;

    @Column(name = "DeletedAt")
    private LocalDateTime deletedAt;
    
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY) 
    @JoinColumn(name = "CategoryId", insertable = false, updatable = false)
    private IngredientCategory ingredientCategory;
    
}
