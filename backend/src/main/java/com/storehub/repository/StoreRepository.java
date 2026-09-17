package com.storehub.repository;

import com.storehub.entity.Store;
import com.storehub.entity.StoreStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StoreRepository extends JpaRepository<Store, Long> {

    Optional<Store> findByStoreCodeIgnoreCase(String storeCode);

    boolean existsByStoreCodeIgnoreCase(String storeCode);

    boolean existsByStoreCodeIgnoreCaseAndIdNot(String storeCode, Long id);

    List<Store> findByStatus(StoreStatus status);

    @Query("SELECT s FROM Store s WHERE " +
            "(:search IS NULL OR :search = '' OR LOWER(s.storeName) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "OR LOWER(s.storeCode) LIKE LOWER(CONCAT('%', :search, '%'))) " +
            "AND (:status IS NULL OR s.status = :status)")
    Page<Store> search(@Param("search") String search, @Param("status") StoreStatus status, Pageable pageable);
}
