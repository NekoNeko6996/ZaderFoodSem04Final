package com.group02.zaderfood.repository;

import com.group02.zaderfood.entity.UserProfile;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;

public interface UserProfileRepository extends JpaRepository<UserProfile, Integer> {

    Optional<UserProfile> findByUserId(Integer userId);

    @Query("SELECT p.goal, COUNT(p) FROM UserProfile p GROUP BY p.goal")
    List<Object[]> countUsersByGoal();
}
