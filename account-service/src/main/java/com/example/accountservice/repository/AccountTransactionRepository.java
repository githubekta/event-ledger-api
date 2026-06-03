package com.example.accountservice.repository;

import com.example.accountservice.entity.AccountTransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface AccountTransactionRepository extends JpaRepository<AccountTransactionEntity, Long> {

    Optional<AccountTransactionEntity> findByEventId(String eventId);

    List<AccountTransactionEntity> findByAccountIdOrderByEventTimestampAscEventIdAsc(String accountId);

    boolean existsByEventId(String eventId);
}

