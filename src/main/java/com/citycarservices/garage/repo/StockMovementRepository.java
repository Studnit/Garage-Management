package com.citycarservices.garage.repo;

import com.citycarservices.garage.domain.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {
    List<StockMovement> findByPartIdOrderByCreatedAtDescIdDesc(Long partId, Pageable pageable);

    List<StockMovement> findByJobCardIdOrderByIdAsc(Long jobCardId);

    List<StockMovement> findByTypeAndCreatedAtBetween(StockMovementType type, LocalDateTime from, LocalDateTime to);
}
