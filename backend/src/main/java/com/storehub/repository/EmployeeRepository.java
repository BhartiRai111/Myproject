package com.storehub.repository;

import com.storehub.entity.Employee;
import com.storehub.entity.EmployeeStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    boolean existsByEmployeeCodeIgnoreCase(String employeeCode);

    boolean existsByEmployeeCodeIgnoreCaseAndIdNot(String employeeCode, Long id);

    boolean existsByMobile(String mobile);

    boolean existsByMobileAndIdNot(String mobile, Long id);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    @Query("SELECT e FROM Employee e WHERE " +
            "(:search IS NULL OR :search = '' OR LOWER(e.name) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "OR LOWER(e.employeeCode) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "OR e.mobile LIKE CONCAT('%', :search, '%')) " +
            "AND (:status IS NULL OR e.status = :status)")
    Page<Employee> search(@Param("search") String search, @Param("status") EmployeeStatus status, Pageable pageable);
}
