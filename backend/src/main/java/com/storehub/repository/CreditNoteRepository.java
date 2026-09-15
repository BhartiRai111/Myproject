package com.storehub.repository;

import com.storehub.entity.CreditNote;
import com.storehub.entity.NoteStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

public interface CreditNoteRepository extends JpaRepository<CreditNote, Long> {

    @Query("SELECT c FROM CreditNote c LEFT JOIN c.customer cust WHERE " +
            "(:search IS NULL OR :search = '' OR " +
            "  LOWER(c.voucherNumber) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "  LOWER(cust.firstName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "  LOWER(cust.lastName) LIKE LOWER(CONCAT('%', :search, '%'))) " +
            "AND (:status IS NULL OR c.status = :status) " +
            "AND (:fromDate IS NULL OR c.noteDate >= :fromDate) " +
            "AND (:toDate IS NULL OR c.noteDate <= :toDate)")
    Page<CreditNote> search(@Param("search") String search, @Param("status") NoteStatus status,
                             @Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate, Pageable pageable);

    @Query("SELECT c.id FROM CreditNote c WHERE c.status = com.storehub.entity.NoteStatus.POSTED")
    java.util.List<Long> findPostedIds();

    @Query("SELECT c.id FROM CreditNote c")
    java.util.List<Long> findAllIds();
}
