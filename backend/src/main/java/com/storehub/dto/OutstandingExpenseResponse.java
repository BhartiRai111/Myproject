package com.storehub.dto;

import com.storehub.entity.Expense;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Builder
@AllArgsConstructor
public class OutstandingExpenseResponse {

    private Long expenseId;
    private String expenseNumber;
    private LocalDate expenseDate;
    private String category;
    private BigDecimal totalAmount;
    private BigDecimal paidAmount;
    private BigDecimal payableAmount;

    public static OutstandingExpenseResponse fromEntity(Expense expense) {
        return OutstandingExpenseResponse.builder()
                .expenseId(expense.getId())
                .expenseNumber(expense.getExpenseNumber())
                .expenseDate(expense.getExpenseDate())
                .category(expense.getCategory())
                .totalAmount(expense.getTotalAmount())
                .paidAmount(expense.getPaidAmount())
                .payableAmount(expense.getPayableAmount())
                .build();
    }
}
