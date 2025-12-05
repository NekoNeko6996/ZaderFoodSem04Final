package com.group02.zaderfood.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.*;
import org.hibernate.annotations.Nationalized;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "ShoppingLists")
public class ShoppingList implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ListId")
    private Integer listId;

    @Column(name = "UserId")
    private Integer userId;

    @Column(name = "Name")
    @Nationalized
    private String name; // Ví dụ: "Đi chợ tuần 4 tháng 12"

    @Column(name = "FromDate")
    private LocalDate fromDate;

    @Column(name = "ToDate")
    private LocalDate toDate;

    @Column(name = "Status")
    private String status; // Mặc định: 'PENDING'

    @Column(name = "CreatedAt")
    private LocalDateTime createdAt;
}