package com.henrymorgandibie.switchyard.transaction.repository;

import com.henrymorgandibie.switchyard.transaction.domain.Reversal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ReversalRepository extends JpaRepository<Reversal, UUID> {

    List<Reversal> findByOriginalTransactionId(UUID originalTransactionId);
}
