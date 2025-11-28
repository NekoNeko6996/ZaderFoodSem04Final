package com.group02.zaderfood.repository;

import com.group02.zaderfood.entity.User;
import com.group02.zaderfood.entity.enums.UserRole;
import com.group02.zaderfood.entity.enums.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Integer> {

    Optional<User> findByEmail(String email);

    @Query("SELECT u FROM User u WHERE "
            + "u.userId != :currentUserId AND "
            + "(:keyword IS NULL OR :keyword = '' OR u.fullName LIKE %:keyword% OR u.email LIKE %:keyword%) AND "
            + "(:role IS NULL OR u.role = :role) AND "
            + "(:status IS NULL OR u.status = :status)")
    Page<User> searchUsers(@Param("currentUserId") Integer currentUserId, // Thêm tham số này
            @Param("keyword") String keyword,
            @Param("role") UserRole role,
            @Param("status") UserStatus status,
            Pageable pageable);

    boolean existsByEmail(String email);
}
