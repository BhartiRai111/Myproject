package com.storehub.repository;

import com.storehub.entity.AccountGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AccountGroupRepository extends JpaRepository<AccountGroup, Long> {
    Optional<AccountGroup> findByNameIgnoreCase(String name);

    List<AccountGroup> findByActiveTrueOrderByNameAsc();
}
