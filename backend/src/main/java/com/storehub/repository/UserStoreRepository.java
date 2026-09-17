package com.storehub.repository;

import com.storehub.entity.UserStore;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserStoreRepository extends JpaRepository<UserStore, Long> {

    List<UserStore> findByUserId(Long userId);

    boolean existsByUserIdAndStoreId(Long userId, Long storeId);

    void deleteByUserId(Long userId);

    long countByStoreId(Long storeId);
}
