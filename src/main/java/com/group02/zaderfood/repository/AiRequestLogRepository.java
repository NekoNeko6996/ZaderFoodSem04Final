package com.group02.zaderfood.repository;

import com.group02.zaderfood.entity.AiRequestLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface AiRequestLogRepository extends JpaRepository<AiRequestLog, Integer> {

    // Tính tổng token đã dùng
    @Query("SELECT SUM(a.tokensUsed) FROM AiRequestLog a")
    Long getTotalTokensUsed();
}