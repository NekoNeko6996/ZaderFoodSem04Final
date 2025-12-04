package com.group02.zaderfood.repository;

import com.group02.zaderfood.entity.UserDietaryPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserDietaryPreferenceRepository extends JpaRepository<UserDietaryPreference, Integer> {
    List<UserDietaryPreference> findByUserId(Integer userId);
    void deleteByUserId(Integer userId);
}