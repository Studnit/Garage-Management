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

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {
    Optional<Vehicle> findByRegNo(String regNo);

    boolean existsByRegNo(String regNo);

    boolean existsByVin(String vin);

    List<Vehicle> findByCustomerIdOrderByIdAsc(Long customerId);

    List<Vehicle> findByActiveTrue();

    @Query("select v from Vehicle v where lower(v.regNo) like lower(concat('%', :q, '%')) "
            + "or lower(v.make) like lower(concat('%', :q, '%')) "
            + "or lower(v.model) like lower(concat('%', :q, '%')) "
            + "or lower(v.customer.name) like lower(concat('%', :q, '%')) "
            + "or v.customer.mobile like concat('%', :q, '%') order by v.regNo")
    List<Vehicle> search(@Param("q") String q, Pageable pageable);
}
