package com.storehub.repository;

import com.storehub.entity.DebitNote;
import com.storehub.entity.NoteStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

public interface DebitNoteRepository extends JpaRepository<DebitNote, Long> {

    @Query("SELECT d FROM DebitNote d LEFT JOIN d.supplier sup WHERE " +
            "(:search IS NULL OR :search = '' OR " +
            "  LOWER(d.voucherNumber) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "  LOWER(sup.name) LIKE LOWER(CONCAT('%', :search, '%'))) " +
            "AND (:status IS NULL OR d.status = :status) " +
            "AND (:fromDate IS NULL OR d.noteDate >= :fromDate) " +
            "AND (:toDate IS NULL OR d.noteDate <= :toDate) " +
            "AND (:storeId IS NULL OR d.sourcePurchase.store.id = :storeId)")
    Page<DebitNote> search(@Param("search") String search, @Param("status") NoteStatus status,
                            @Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate,
                            @Param("storeId") Long storeId, Pageable pageable);

    @Query("SELECT d.id FROM DebitNote d WHERE d.status = com.storehub.entity.NoteStatus.POSTED")
    java.util.List<Long> findPostedIds();

    @Query("SELECT d.id FROM DebitNote d")
    java.util.List<Long> findAllIds();
}
