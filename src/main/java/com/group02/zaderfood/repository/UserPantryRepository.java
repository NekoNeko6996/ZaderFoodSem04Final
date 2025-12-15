// UserPantryRepository.java
package com.group02.zaderfood.repository;

import com.group02.zaderfood.entity.UserPantry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface UserPantryRepository extends JpaRepository<UserPantry, Integer> {
    
    // Lấy danh sách đồ trong tủ lạnh của User, sắp xếp đồ sắp hết hạn lên đầu
    List<UserPantry> findByUserIdOrderByExpiryDateAsc(Integer userId);
    
    // Tìm xem user đã có nguyên liệu này chưa (để cộng dồn số lượng thay vì tạo dòng mới)
    UserPantry findByUserIdAndIngredientId(Integer userId, Integer ingredientId);
}