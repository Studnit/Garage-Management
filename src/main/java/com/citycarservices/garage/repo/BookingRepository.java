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

public interface BookingRepository extends JpaRepository<Booking, Long> {
    Optional<Booking> findByBookingNo(String bookingNo);

    List<Booking> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

    List<Booking> findByVehicleIdOrderByCreatedAtDesc(Long vehicleId);

    List<Booking> findByStatusInOrderByPreferredAtAsc(Collection<BookingStatus> statuses);

    List<Booking> findByPreferredAtBetweenOrderByPreferredAtAsc(LocalDateTime from, LocalDateTime to);

    long countByStatus(BookingStatus status);

    long countByVehicleIdAndStatusIn(Long vehicleId, Collection<BookingStatus> statuses);
}
