package com.group02.zaderfood.dto;

import com.group02.zaderfood.entity.RecipeCollection;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CollectionDTO {
    private RecipeCollection collection;
    private int itemCount;
}