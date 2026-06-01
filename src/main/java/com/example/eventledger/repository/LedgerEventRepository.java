package com.example.eventledger.repository;

import com.example.eventledger.entity.LedgerEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LedgerEventRepository extends JpaRepository<LedgerEvent, String> {

    Optional<LedgerEvent> findByEventId(String eventId);

    List<LedgerEvent> findByAccountIdOrderByEventTimestampAscEventIdAsc(String accountId);

    Page<LedgerEvent> findByAccountIdOrderByEventTimestampAscEventIdAsc(String accountId, Pageable pageable);

    @Query("SELECT DISTINCT e.currency FROM LedgerEvent e WHERE e.accountId = :accountId")
    List<String> findDistinctCurrenciesByAccountId(@Param("accountId") String accountId);

    boolean existsByAccountIdAndCurrencyNot(String accountId, String currency);
}

