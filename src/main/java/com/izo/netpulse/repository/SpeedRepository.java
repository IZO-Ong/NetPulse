package com.izo.netpulse.repository;

import com.izo.netpulse.model.SpeedTestResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository interface for {@link SpeedTestResult} entities.
 * Provides standard CRUD operations and database interaction logic via Spring Data JPA.
 */
@Repository
public interface SpeedRepository extends JpaRepository<SpeedTestResult, Long> {
}