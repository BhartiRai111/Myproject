package com.storehub.repository;

import com.storehub.entity.PaymentMethod;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentMethodRepository extends JpaRepository<PaymentMethod, Long> {

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    List<PaymentMethod> findByActiveTrueOrderBySortOrderAscNameAsc();

    List<PaymentMethod> findAllByOrderBySortOrderAscNameAsc();
}
