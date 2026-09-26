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

public interface JobCardRepository extends JpaRepository<JobCard, Long> {
    Optional<JobCard> findByJobNo(String jobNo);

    Optional<JobCard> findByBookingId(Long bookingId);

    List<JobCard> findByMechanicIdOrderByCreatedAtDesc(Long mechanicId);

    List<JobCard> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

    List<JobCard> findByVehicleIdOrderByCreatedAtDesc(Long vehicleId);

    List<JobCard> findByBookingStatusInOrderByCreatedAtDesc(Collection<BookingStatus> statuses);

    List<JobCard> findByStatusOrderByCreatedAtDesc(JobStatus status);
}
