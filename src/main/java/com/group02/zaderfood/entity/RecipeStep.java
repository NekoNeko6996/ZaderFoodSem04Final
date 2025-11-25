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
@Table(name = "RecipeSteps")
public class RecipeStep implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "StepId")
    private Integer stepId;

    @Column(name = "RecipeId")
    private Integer recipeId;

    @Column(name = "StepNumber")
    private Integer stepNumber;

    @Column(name = "Instruction")
    private String instruction;

    @Column(name = "MediaUrl")
    private String mediaUrl;

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
    @JoinColumn(name = "RecipeId", insertable = false, updatable = false)
    private Recipe recipe;

}
