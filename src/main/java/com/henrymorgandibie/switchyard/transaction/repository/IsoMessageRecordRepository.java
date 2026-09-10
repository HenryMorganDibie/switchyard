package com.henrymorgandibie.switchyard.transaction.repository;

import com.henrymorgandibie.switchyard.transaction.domain.IsoMessageRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface IsoMessageRecordRepository extends JpaRepository<IsoMessageRecord, UUID> {

    List<IsoMessageRecord> findByTransactionIdOrderByCapturedAtAsc(UUID transactionId);
}
