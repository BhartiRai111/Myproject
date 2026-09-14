package com.storehub.dto;

import com.storehub.entity.GstType;
import com.storehub.entity.PaymentMode;
import com.storehub.entity.TaxMode;
import com.storehub.entity.TransactionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
public class PurchaseCreateRequest {

    @NotNull(message = "Supplier is required")
    private Long supplierId;

    @NotNull(message = "Purchase date is required")
    private LocalDate purchaseDate;

    @NotNull(message = "Purchase type is required")
    private GstType gstType;

    /** Required when gstType is GST: whether GST splits as CGST+SGST or IGST. */
    private TaxMode taxMode;

    private String supplierPhone;

    private String supplierGstin;

    private String billingAddress;

    private String shippingAddress;

    @NotNull(message = "Payment mode is required")
    private PaymentMode paymentMode;

    @NotNull(message = "Paid amount is required")
    @DecimalMin(value = "0", message = "Paid amount cannot be negative")
    private BigDecimal paidAmount = BigDecimal.ZERO;

    private String notes;

    /** Set when this bill originates from a Purchase Order. */
    private Long purchaseOrderId;

    @NotEmpty(message = "At least one purchase item is required")
    @Valid
    private List<PurchaseItemRequest> items;

    /**
     * PURCHASE (default) for a normal GST/Non-GST bill, or PURCHASE_CHALLAN
     * for a Kacchi Purchase. Fixed at creation; determines the voucher-number
     * prefix and whether the purchase is eligible for GST return reporting.
     */
    private TransactionType transactionType;

    /** Kacchi Purchase only: save without posting (no stock/ledger/GST-log/accounting effects yet). */
    private boolean saveAsDraft = false;
}
