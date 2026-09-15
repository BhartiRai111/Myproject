package com.storehub.service;

import com.storehub.dto.FinancialYearRequest;
import com.storehub.dto.FinancialYearResponse;
import com.storehub.dto.FinancialYearSummaryResponse;
import com.storehub.entity.FinancialYear;
import com.storehub.entity.FinancialYearStatus;
import com.storehub.exception.BadRequestException;
import com.storehub.exception.FinancialYearNotFoundException;
import com.storehub.repository.FinancialYearRepository;
import com.storehub.repository.PurchaseRepository;
import com.storehub.repository.SaleRepository;
import com.storehub.util.FinancialYearUtil;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Single source of truth for "which financial year does this date belong
 * to, and is it OPEN". Every posting path (Sale/Purchase/Receipt/Payment/
 * Credit-Debit Note, all funnelled through {@link AccountingService#postJournal})
 * calls {@link #resolveOpenForPosting(LocalDate)} before posting, so FY
 * enforcement lives in exactly one place rather than being re-checked (or
 * forgotten) in every service. {@link VoucherNumberService} calls
 * {@link #resolveForDate(LocalDate)} for the FY code used in voucher
 * numbers, which does not require the year to be OPEN (a closed FY's
 * historical documents must still be viewable/re-printable).
 */
@Service
@RequiredArgsConstructor
public class FinancialYearService {

    private final FinancialYearRepository financialYearRepository;
    private final SaleRepository saleRepository;
    private final PurchaseRepository purchaseRepository;
    private final AuditService auditService;

    /** Idempotent: ensures at least one FinancialYear row exists, covering "today", on first startup. */
    @PostConstruct
    @Transactional
    public void ensureCurrentFinancialYearExists() {
        LocalDate today = LocalDate.now();
        if (financialYearRepository.findByDate(today).isPresent()) {
            return;
        }
        FinancialYear fy = buildFor(today);
        fy.setCurrent(true);
        financialYearRepository.save(fy);
    }

    private FinancialYear buildFor(LocalDate date) {
        LocalDate start = FinancialYearUtil.startOf(date);
        LocalDate end = FinancialYearUtil.endOf(date);
        String code = FinancialYearUtil.shortCode(date);
        return FinancialYear.builder()
                .name("FY " + FinancialYearUtil.label(date))
                .code(code)
                .startDate(start)
                .endDate(end)
                .status(FinancialYearStatus.OPEN)
                .current(false)
                .build();
    }

    @Transactional(readOnly = true)
    public List<FinancialYearResponse> list() {
        return financialYearRepository.findAllByOrderByStartDateDesc().stream()
                .map(FinancialYearResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public FinancialYearResponse getById(Long id) {
        return FinancialYearResponse.fromEntity(findOrThrow(id));
    }

    @Transactional(readOnly = true)
    public FinancialYearResponse getCurrent() {
        FinancialYear current = financialYearRepository.findByCurrentTrue()
                .orElseThrow(() -> new BadRequestException("No current financial year is set"));
        return FinancialYearResponse.fromEntity(current);
    }

    /** Resolves the FY a date falls in, for numbering/reporting — does not require it to be OPEN. */
    @Transactional(readOnly = true)
    public FinancialYear resolveForDate(LocalDate date) {
        return financialYearRepository.findByDate(date)
                .orElseThrow(() -> new BadRequestException(
                        "No financial year is defined for " + date + ". Ask an ADMIN to create it under Financial Years."));
    }

    /** Resolves the FY for a date AND requires it to be OPEN — the gate every posting path must pass through. */
    @Transactional(readOnly = true)
    public FinancialYear resolveOpenForPosting(LocalDate date) {
        FinancialYear fy = resolveForDate(date);
        if (fy.getStatus() == FinancialYearStatus.CLOSED) {
            throw new BadRequestException(
                    "Financial year " + fy.getName() + " is closed. This transaction (" + date + ") cannot be posted.");
        }
        return fy;
    }

    @Transactional
    public FinancialYearResponse create(FinancialYearRequest request) {
        if (!request.getEndDate().isAfter(request.getStartDate())) {
            throw new BadRequestException("End date must be after start date");
        }
        String code = request.getCode() != null && !request.getCode().isBlank()
                ? request.getCode()
                : FinancialYearUtil.shortCode(request.getStartDate());
        if (financialYearRepository.existsByCodeIgnoreCase(code)) {
            throw new BadRequestException("A financial year with code '" + code + "' already exists");
        }
        if (financialYearRepository.countOverlapping(request.getStartDate(), request.getEndDate(), -1L) > 0) {
            throw new BadRequestException("This date range overlaps an existing financial year");
        }

        FinancialYear fy = FinancialYear.builder()
                .name(request.getName() != null && !request.getName().isBlank() ? request.getName() : "FY " + FinancialYearUtil.label(request.getStartDate()))
                .code(code)
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .status(FinancialYearStatus.OPEN)
                .current(false)
                .build();
        FinancialYear saved = financialYearRepository.save(fy);
        auditService.log(com.storehub.entity.AuditAction.CREATE, "ADMIN", "FinancialYear", saved.getId(),
                saved.getCode(), null, null, "Financial year " + saved.getName() + " created");
        return FinancialYearResponse.fromEntity(saved);
    }

    @Transactional
    public FinancialYearResponse setStatus(Long id, FinancialYearStatus status) {
        FinancialYear fy = findOrThrow(id);
        if (fy.isCurrent() && status == FinancialYearStatus.CLOSED) {
            throw new BadRequestException("Cannot close the current financial year. Mark another year current first.");
        }
        FinancialYearStatus oldStatus = fy.getStatus();
        fy.setStatus(status);
        FinancialYear saved = financialYearRepository.save(fy);
        auditService.log(status == FinancialYearStatus.CLOSED ? com.storehub.entity.AuditAction.FY_CLOSE : com.storehub.entity.AuditAction.FY_OPEN,
                "ADMIN", "FinancialYear", saved.getId(), saved.getCode(), oldStatus.name(), status.name(),
                "Financial year " + saved.getName() + " status changed from " + oldStatus + " to " + status);
        return FinancialYearResponse.fromEntity(saved);
    }

    /** Marks one FY current, unmarking any other — never more than one current row at a time. */
    @Transactional
    public FinancialYearResponse markCurrent(Long id) {
        FinancialYear target = findOrThrow(id);
        if (target.getStatus() == FinancialYearStatus.CLOSED) {
            throw new BadRequestException("Cannot mark a closed financial year as current. Re-open it first.");
        }
        financialYearRepository.findByCurrentTrue().ifPresent(existing -> {
            if (!existing.getId().equals(target.getId())) {
                existing.setCurrent(false);
                financialYearRepository.save(existing);
            }
        });
        target.setCurrent(true);
        FinancialYear saved = financialYearRepository.save(target);
        auditService.log(com.storehub.entity.AuditAction.UPDATE, "ADMIN", "FinancialYear", saved.getId(),
                saved.getCode(), null, null, "Financial year " + saved.getName() + " marked as current");
        return FinancialYearResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public FinancialYearSummaryResponse summary(Long id) {
        FinancialYear fy = findOrThrow(id);
        BigDecimal totalSales = saleRepository.sumTotalAmountByDateRange(fy.getStartDate(), fy.getEndDate());
        BigDecimal totalPurchases = purchaseRepository.sumTotalAmountByDateRange(fy.getStartDate(), fy.getEndDate());
        long saleCount = saleRepository.countByDateRange(fy.getStartDate(), fy.getEndDate());
        long purchaseCount = purchaseRepository.countByDateRange(fy.getStartDate(), fy.getEndDate());
        return FinancialYearSummaryResponse.builder()
                .financialYear(FinancialYearResponse.fromEntity(fy))
                .totalSales(totalSales != null ? totalSales : BigDecimal.ZERO)
                .totalPurchases(totalPurchases != null ? totalPurchases : BigDecimal.ZERO)
                .saleCount(saleCount)
                .purchaseCount(purchaseCount)
                .build();
    }

    private FinancialYear findOrThrow(Long id) {
        return financialYearRepository.findById(id).orElseThrow(() -> new FinancialYearNotFoundException(id));
    }
}
