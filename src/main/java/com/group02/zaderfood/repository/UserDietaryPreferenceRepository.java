package com.group02.zaderfood.repository;

import com.group02.zaderfood.entity.UserDietaryPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import org.springframework.data.jpa.repository.Query;

@Repository
public interface UserDietaryPreferenceRepository extends JpaRepository<UserDietaryPreference, Integer> {

    List<UserDietaryPreference> findByUserId(Integer userId);

    void deleteByUserId(Integer userId);

    @Query("SELECT d.dietType, COUNT(d) FROM UserDietaryPreference d GROUP BY d.dietType")
    List<Object[]> countByDietType();
}
