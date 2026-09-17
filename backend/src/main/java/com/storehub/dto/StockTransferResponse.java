package com.storehub.dto;

import com.storehub.entity.StockTransfer;
import com.storehub.entity.StockTransferStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Builder
@AllArgsConstructor
public class StockTransferResponse {
    private Long id;
    private String transferNumber;
    private LocalDate transferDate;
    private Long fromStoreId;
    private String fromStoreName;
    private String fromStoreCode;
    private Long toStoreId;
    private String toStoreName;
    private String toStoreCode;
    private StockTransferStatus status;
    private String remarks;
    private List<StockTransferItemResponse> items;
    private String createdBy;
    private LocalDateTime createdAt;
    private String approvedBy;
    private LocalDateTime approvedAt;
    private String dispatchedBy;
    private LocalDateTime dispatchedAt;
    private String receivedBy;
    private LocalDateTime receivedAt;
    private String cancelledBy;
    private LocalDateTime cancelledAt;
    private String cancellationReason;

    public static StockTransferResponse fromEntity(StockTransfer t) {
        return StockTransferResponse.builder()
                .id(t.getId())
                .transferNumber(t.getTransferNumber())
                .transferDate(t.getTransferDate())
                .fromStoreId(t.getFromStore().getId())
                .fromStoreName(t.getFromStore().getStoreName())
                .fromStoreCode(t.getFromStore().getStoreCode())
                .toStoreId(t.getToStore().getId())
                .toStoreName(t.getToStore().getStoreName())
                .toStoreCode(t.getToStore().getStoreCode())
                .status(t.getStatus())
                .remarks(t.getRemarks())
                .items(t.getItems().stream().map(StockTransferItemResponse::fromEntity).collect(Collectors.toList()))
                .createdBy(t.getCreatedBy())
                .createdAt(t.getCreatedAt())
                .approvedBy(t.getApprovedBy())
                .approvedAt(t.getApprovedAt())
                .dispatchedBy(t.getDispatchedBy())
                .dispatchedAt(t.getDispatchedAt())
                .receivedBy(t.getReceivedBy())
                .receivedAt(t.getReceivedAt())
                .cancelledBy(t.getCancelledBy())
                .cancelledAt(t.getCancelledAt())
                .cancellationReason(t.getCancellationReason())
                .build();
    }
}
