package com.storehub.repository;

import com.storehub.entity.DayClosing;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DayClosingRepository extends JpaRepository<DayClosing, Long> {

    boolean existsByClosingDate(LocalDate closingDate);

    Optional<DayClosing> findByClosingDate(LocalDate closingDate);

    List<DayClosing> findAllByOrderByClosingDateDesc();
}
