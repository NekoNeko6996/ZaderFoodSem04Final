package com.group02.zaderfood.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "UserWeightHistory")
public class UserWeightHistory implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "WeightHistoryId")
    private Integer weightHistoryId;

    @Column(name = "UserId")
    private Integer userId;

    @Column(name = "WeightKg")
    private BigDecimal weightKg;

    @Column(name = "RecordedAt")
    private LocalDateTime recordedAt;
}