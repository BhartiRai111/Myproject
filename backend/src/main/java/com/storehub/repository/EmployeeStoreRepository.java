package com.storehub.repository;

import com.storehub.entity.EmployeeStore;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmployeeStoreRepository extends JpaRepository<EmployeeStore, Long> {

    List<EmployeeStore> findByEmployeeId(Long employeeId);

    void deleteByEmployeeId(Long employeeId);
}
