package com.storehub.repository;

import com.storehub.entity.CreditNoteItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CreditNoteItemRepository extends JpaRepository<CreditNoteItem, Long> {

    /**
     * Total quantity already returned against one SaleItem across every note that is not
     * CANCELLED (DRAFT notes count too — a draft still reserves the quantity it claims, so
     * two concurrent drafts can't both claim the same units; see CreditNoteService for the
     * "cannot return already-returned quantity" validation this backs).
     */
    @Query("SELECT COALESCE(SUM(i.quantity), 0) FROM CreditNoteItem i " +
            "WHERE i.saleItem.id = :saleItemId AND i.creditNote.status <> com.storehub.entity.NoteStatus.CANCELLED")
    int sumReturnedQuantity(@Param("saleItemId") Long saleItemId);
}
