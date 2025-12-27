// UserPantryRepository.java
package com.group02.zaderfood.repository;

import com.group02.zaderfood.entity.UserPantry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface UserPantryRepository extends JpaRepository<UserPantry, Integer> {
    List<UserPantry> findByUserIdOrderByExpiryDateAsc(Integer userId);
    UserPantry findByUserIdAndIngredientId(Integer userId, Integer ingredientId);
}