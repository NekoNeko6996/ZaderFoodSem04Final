package com.group02.zaderfood.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "SystemLogs")
public class SystemLog implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "LogId")
    private Integer logId;

    @Column(name = "ActionType")
    private String actionType;

    @Column(name = "UserId")
    private Integer userId;

    @Column(name = "IpAddress")
    private String ipAddress;

    @Column(name = "Details")
    private String details;

    @Column(name = "CreatedAt")
    private LocalDateTime createdAt;

}
