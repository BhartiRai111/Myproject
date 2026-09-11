package com.storehub.repository;

import com.storehub.entity.Account;
import com.storehub.entity.AccountType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByAccountCode(String accountCode);

    boolean existsByAccountCodeIgnoreCase(String accountCode);

    List<Account> findByActiveTrueOrderByAccountNameAsc();

    @Query("SELECT a FROM Account a WHERE " +
            "(:search IS NULL OR :search = '' OR " +
            "  LOWER(a.accountCode) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "  LOWER(a.accountName) LIKE LOWER(CONCAT('%', :search, '%'))) " +
            "AND (:accountType IS NULL OR a.accountType = :accountType) " +
            "AND (:active IS NULL OR a.active = :active)")
    Page<Account> search(@Param("search") String search,
                          @Param("accountType") AccountType accountType,
                          @Param("active") Boolean active,
                          Pageable pageable);
}
