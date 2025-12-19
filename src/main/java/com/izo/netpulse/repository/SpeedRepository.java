package com.izo.netpulse.repository;

import com.izo.netpulse.model.SpeedTestResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SpeedRepository extends JpaRepository<SpeedTestResult, Long> {
}