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

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {
    Optional<Feedback> findByBookingId(Long bookingId);

    List<Feedback> findByCustomerIdOrderByIdDesc(Long customerId);

    List<Feedback> findByPublishedTrueOrderByIdDesc(Pageable pageable);
}
