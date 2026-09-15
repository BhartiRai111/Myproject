package com.storehub.repository;

import com.storehub.entity.DebitNoteItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DebitNoteItemRepository extends JpaRepository<DebitNoteItem, Long> {

    @Query("SELECT COALESCE(SUM(i.quantity), 0) FROM DebitNoteItem i " +
            "WHERE i.purchaseItem.id = :purchaseItemId AND i.debitNote.status <> com.storehub.entity.NoteStatus.CANCELLED")
    int sumReturnedQuantity(@Param("purchaseItemId") Long purchaseItemId);
}
