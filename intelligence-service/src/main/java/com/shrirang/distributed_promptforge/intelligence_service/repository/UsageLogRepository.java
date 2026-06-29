package com.shrirang.distributed_promptforge.intelligence_service.repository;

import com.shrirang.distributed_promptforge.intelligence_service.entity.UsageLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

public interface UsageLogRepository extends JpaRepository<UsageLog, Long> {

    Optional<UsageLog> findByUserIdAndDate(Long userId, LocalDate today);

    /**
     * Atomic upsert — inserts a new row if none exists for (userId, date),
     * otherwise increments the existing tokensUsed counter.
     * Prevents the race condition of concurrent find + save.
     */
    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO usage_logs (user_id, date, tokens_used)
            VALUES (:userId, :date, :tokens)
            ON CONFLICT (user_id, date)
            DO UPDATE SET tokens_used = usage_logs.tokens_used + EXCLUDED.tokens_used
            """, nativeQuery = true)
    void incrementTokens(@Param("userId") Long userId,
                         @Param("date") LocalDate date,
                         @Param("tokens") int tokens);
}
