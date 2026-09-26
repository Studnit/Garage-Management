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

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    Optional<Customer> findByMobile(String mobile);

    boolean existsByMobile(String mobile);

    Optional<Customer> findByUserId(Long userId);

    @Query("select c from Customer c where lower(c.name) like lower(concat('%', :q, '%')) "
            + "or c.mobile like concat('%', :q, '%') "
            + "or lower(coalesce(c.email, '')) like lower(concat('%', :q, '%')) "
            + "or lower(coalesce(c.code, '')) like lower(concat('%', :q, '%')) order by c.name")
    List<Customer> search(@Param("q") String q, Pageable pageable);
}
