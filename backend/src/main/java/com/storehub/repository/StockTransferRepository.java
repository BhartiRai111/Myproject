package com.storehub.repository;

import com.storehub.entity.StockTransfer;
import com.storehub.entity.StockTransferStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

public interface StockTransferRepository extends JpaRepository<StockTransfer, Long> {

    /** {@code storeId} matches EITHER side of the transfer — a store-scoped user sees a transfer if it touches any store they can access. */
    @Query("SELECT t FROM StockTransfer t WHERE " +
            "(:search IS NULL OR :search = '' OR LOWER(t.transferNumber) LIKE LOWER(CONCAT('%', :search, '%'))) " +
            "AND (:status IS NULL OR t.status = :status) " +
            "AND (:fromDate IS NULL OR t.transferDate >= :fromDate) " +
            "AND (:toDate IS NULL OR t.transferDate <= :toDate) " +
            "AND (:storeId IS NULL OR t.fromStore.id = :storeId OR t.toStore.id = :storeId)")
    Page<StockTransfer> search(@Param("search") String search,
                                @Param("status") StockTransferStatus status,
                                @Param("fromDate") LocalDate fromDate,
                                @Param("toDate") LocalDate toDate,
                                @Param("storeId") Long storeId,
                                Pageable pageable);
}
