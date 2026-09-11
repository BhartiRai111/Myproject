package com.storehub.dto;

import com.storehub.entity.JournalHeader;
import com.storehub.entity.JournalStatus;
import com.storehub.entity.VoucherType;
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
public class JournalHeaderResponse {
    private Long id;
    private String journalNumber;
    private LocalDate journalDate;
    private VoucherType voucherType;
    private Long voucherId;
    private String voucherNumber;
    private String narration;
    private JournalStatus status;
    private Long reversalOfJournalId;
    private String postedBy;
    private LocalDateTime postedAt;
    private String createdBy;
    private LocalDateTime createdAt;
    private BigDecimal totalDebit;
    private BigDecimal totalCredit;
    private List<JournalDetailResponse> lines;

    public static JournalHeaderResponse fromEntity(JournalHeader header) {
        List<JournalDetailResponse> lines = header.getLines().stream()
                .map(JournalDetailResponse::fromEntity)
                .toList();
        BigDecimal totalDebit = lines.stream().map(JournalDetailResponse::getDebitAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCredit = lines.stream().map(JournalDetailResponse::getCreditAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        return JournalHeaderResponse.builder()
                .id(header.getId())
                .journalNumber(header.getJournalNumber())
                .journalDate(header.getJournalDate())
                .voucherType(header.getVoucherType())
                .voucherId(header.getVoucherId())
                .voucherNumber(header.getVoucherNumber())
                .narration(header.getNarration())
                .status(header.getStatus())
                .reversalOfJournalId(header.getReversalOfJournal() != null ? header.getReversalOfJournal().getId() : null)
                .postedBy(header.getPostedBy())
                .postedAt(header.getPostedAt())
                .createdBy(header.getCreatedBy())
                .createdAt(header.getCreatedAt())
                .totalDebit(totalDebit)
                .totalCredit(totalCredit)
                .lines(lines)
                .build();
    }
}
