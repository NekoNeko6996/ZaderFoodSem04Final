package com.group02.zaderfood.repository;

import com.group02.zaderfood.entity.ShoppingListItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ShoppingListItemRepository extends JpaRepository<ShoppingListItem, Integer> {
    List<ShoppingListItem> findByListId(Integer listId);
}