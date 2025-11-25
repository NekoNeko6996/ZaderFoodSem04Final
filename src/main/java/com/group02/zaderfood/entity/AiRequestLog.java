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
@Table(name = "AiRequestLogs")
public class AiRequestLog implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "RequestId")
    private Integer requestId;

    @Column(name = "UserId")
    private Integer userId;

    @Column(name = "RequestType")
    private String requestType;

    @Column(name = "PromptContent")
    private String promptContent;

    @Column(name = "AiResponse")
    private String aiResponse;

    @Column(name = "TokensUsed")
    private Integer tokensUsed;

    @Column(name = "CreatedAt")
    private LocalDateTime createdAt;

}
