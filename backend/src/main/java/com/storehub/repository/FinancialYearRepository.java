package com.storehub.repository;

import com.storehub.entity.FinancialYear;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FinancialYearRepository extends JpaRepository<FinancialYear, Long> {

    boolean existsByCodeIgnoreCase(String code);

    Optional<FinancialYear> findByCodeIgnoreCase(String code);

    Optional<FinancialYear> findByCurrentTrue();

    @Query("SELECT f FROM FinancialYear f WHERE :date >= f.startDate AND :date <= f.endDate")
    Optional<FinancialYear> findByDate(@Param("date") LocalDate date);

    List<FinancialYear> findAllByOrderByStartDateDesc();

    @Query("SELECT COUNT(f) FROM FinancialYear f WHERE f.id <> :excludeId AND :startDate <= f.endDate AND :endDate >= f.startDate")
    long countOverlapping(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate, @Param("excludeId") Long excludeId);
}
