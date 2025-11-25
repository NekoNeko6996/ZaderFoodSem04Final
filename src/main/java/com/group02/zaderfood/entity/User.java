package com.group02.zaderfood.entity;

import com.group02.zaderfood.entity.enums.UserRole;
import com.group02.zaderfood.entity.enums.UserStatus;
import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "Users")
public class User implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "UserId")
    private Integer userId;

    @Column(name = "Email")
    private String email;

    @Column(name = "PasswordHash")
    private String passwordHash;

    @Column(name = "FullName")
    private String fullName;

    @Column(name = "Role")
    @Enumerated(EnumType.STRING)
    private UserRole role;

    @Column(name = "Status")
    @Enumerated(EnumType.STRING)
    private UserStatus status;

    @Column(name = "IsEmailVerified")
    private Boolean isEmailVerified;

    @Column(name = "LastLoginAt")
    private LocalDateTime lastLoginAt;

    @Column(name = "CreatedAt")
    private LocalDateTime createdAt;

    @Column(name = "UpdatedAt")
    private LocalDateTime updatedAt;

    @Column(name = "IsDeleted")
    private Boolean isDeleted;

    @Column(name = "DeletedAt")
    private LocalDateTime deletedAt;

}
