package com.group02.zaderfood.service;


import com.group02.zaderfood.entity.User;
import com.group02.zaderfood.entity.enums.UserRole;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

public class CustomUserDetails implements UserDetails {

    private final User user;

    public CustomUserDetails(User user) {
        this.user = user;
    }

    // --- Phương thức quan trọng để Controller lấy được UserId ---
    public Integer getUserId() {
        return user.getUserId();
    }
    
    public User getUser() {
        return user;
    }
    
    public UserRole getUserRole() {
        return user.getRole();
    }

    // --- Các phương thức bắt buộc của Spring Security ---

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // Chuyển Role từ DB (ADMIN, USER) thành quyền hạn Spring Security
        // Lưu ý: Spring Security thường yêu cầu prefix "ROLE_"
        return Collections.singleton(new SimpleGrantedAuthority("ROLE_" + user.getRole()));
    }

    @Override
    public String getPassword() {
        return user.getPasswordHash();
    }

    @Override
    public String getUsername() {
        return user.getEmail();
    }
    
    public String getFullName() {
        return user.getFullName(); 
    }
    
    public String getDisplayName() {
        return (user.getFullName() != null && !user.getFullName().isEmpty()) 
               ? user.getFullName() 
               : user.getEmail();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return user.getStatus() != null && "ACTIVE".equals(user.getStatus().name());
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
//        return Boolean.TRUE.equals(user.getIsEmailVerified()); 
        return true;
    }
}