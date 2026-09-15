package com.storehub.dto;

import com.storehub.entity.CreditNote;
import com.storehub.entity.CreditNoteType;
import com.storehub.entity.GstType;
import com.storehub.entity.NoteStatus;
import com.storehub.entity.StockImpactType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class CreditNoteResponse {
    private Long id;
    private String voucherNumber;
    private CreditNoteType noteType;
    private Long sourceSaleId;
    private String sourceSaleInvoiceNumber;
    private CustomerResponse customer;
    private LocalDate noteDate;
    private String reason;
    private String placeOfSupply;
    private GstType gstType;
    private StockImpactType stockImpact;
    private boolean gstReportingApplicable;
    private List<CreditNoteItemResponse> items;
    private BigDecimal taxableAmount;
    private BigDecimal cgstAmount;
    private BigDecimal sgstAmount;
    private BigDecimal igstAmount;
    private BigDecimal totalTax;
    private BigDecimal totalAmount;
    private NoteStatus status;
    private String remarks;
    private String createdBy;
    private String postedBy;
    private LocalDateTime postedAt;
    private String cancelledBy;
    private LocalDateTime cancelledAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static CreditNoteResponse fromEntity(CreditNote note) {
        return CreditNoteResponse.builder()
                .id(note.getId())
                .voucherNumber(note.getVoucherNumber())
                .noteType(note.getNoteType())
                .sourceSaleId(note.getSourceSale().getId())
                .sourceSaleInvoiceNumber(note.getSourceSale().getInvoiceNumber())
                .customer(CustomerResponse.fromEntity(note.getCustomer()))
                .noteDate(note.getNoteDate())
                .reason(note.getReason())
                .placeOfSupply(note.getPlaceOfSupply())
                .gstType(note.getGstType())
                .stockImpact(note.getStockImpact())
                .gstReportingApplicable(Boolean.TRUE.equals(note.getGstReportingApplicable()))
                .items(note.getItems().stream().map(CreditNoteItemResponse::fromEntity).toList())
                .taxableAmount(note.getTaxableAmount())
                .cgstAmount(note.getCgstAmount())
                .sgstAmount(note.getSgstAmount())
                .igstAmount(note.getIgstAmount())
                .totalTax(note.getTotalTax())
                .totalAmount(note.getTotalAmount())
                .status(note.getStatus())
                .remarks(note.getRemarks())
                .createdBy(note.getCreatedBy())
                .postedBy(note.getPostedBy())
                .postedAt(note.getPostedAt())
                .cancelledBy(note.getCancelledBy())
                .cancelledAt(note.getCancelledAt())
                .createdAt(note.getCreatedAt())
                .updatedAt(note.getUpdatedAt())
                .build();
    }
}
