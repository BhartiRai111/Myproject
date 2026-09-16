package com.storehub.service;

import com.storehub.dto.ExpenseCategoryRequest;
import com.storehub.dto.ExpenseCategoryResponse;
import com.storehub.dto.PagedResponse;
import com.storehub.entity.Account;
import com.storehub.entity.ExpenseCategory;
import com.storehub.exception.BadRequestException;
import com.storehub.exception.MasterNotFoundException;
import com.storehub.repository.ExpenseCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ExpenseCategoryService {

    private final ExpenseCategoryRepository expenseCategoryRepository;
    private final AccountService accountService;

    public PagedResponse<ExpenseCategoryResponse> search(String search, Boolean active, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("name").ascending());
        Page<ExpenseCategory> result = expenseCategoryRepository.search(search, active, pageable);
        return PagedResponse.fromPage(result.map(ExpenseCategoryResponse::fromEntity));
    }

    /** Active categories only — what should populate the Category dropdown on a new Expense. */
    public List<ExpenseCategoryResponse> listActive() {
        return expenseCategoryRepository.findByActiveTrueOrderByNameAsc().stream()
                .map(ExpenseCategoryResponse::fromEntity).toList();
    }

    public ExpenseCategoryResponse getById(Long id) {
        return ExpenseCategoryResponse.fromEntity(findOrThrow(id));
    }

    @Transactional
    public ExpenseCategoryResponse create(ExpenseCategoryRequest request) {
        if (expenseCategoryRepository.existsByCodeIgnoreCase(request.getCode())) {
            throw new BadRequestException("An expense category with code '" + request.getCode() + "' already exists");
        }
        if (expenseCategoryRepository.existsByNameIgnoreCase(request.getName())) {
            throw new BadRequestException("An expense category named '" + request.getName() + "' already exists");
        }

        ExpenseCategory category = ExpenseCategory.builder()
                .code(request.getCode())
                .name(request.getName())
                .linkedAccount(resolveLinkedAccount(request.getLinkedAccountId()))
                .description(request.getDescription())
                .active(true)
                .build();

        return ExpenseCategoryResponse.fromEntity(expenseCategoryRepository.save(category));
    }

    @Transactional
    public ExpenseCategoryResponse update(Long id, ExpenseCategoryRequest request) {
        ExpenseCategory category = findOrThrow(id);

        if (!category.getCode().equalsIgnoreCase(request.getCode())
                && expenseCategoryRepository.existsByCodeIgnoreCaseAndIdNot(request.getCode(), id)) {
            throw new BadRequestException("An expense category with code '" + request.getCode() + "' already exists");
        }
        if (!category.getName().equalsIgnoreCase(request.getName())
                && expenseCategoryRepository.existsByNameIgnoreCaseAndIdNot(request.getName(), id)) {
            throw new BadRequestException("An expense category named '" + request.getName() + "' already exists");
        }

        category.setCode(request.getCode());
        category.setName(request.getName());
        category.setLinkedAccount(resolveLinkedAccount(request.getLinkedAccountId()));
        category.setDescription(request.getDescription());

        return ExpenseCategoryResponse.fromEntity(expenseCategoryRepository.save(category));
    }

    /** Never a hard delete: a category already referenced by historical expenses must stay visible on them. */
    @Transactional
    public ExpenseCategoryResponse setActive(Long id, boolean active) {
        ExpenseCategory category = findOrThrow(id);
        category.setActive(active);
        return ExpenseCategoryResponse.fromEntity(expenseCategoryRepository.save(category));
    }

    private Account resolveLinkedAccount(Long accountId) {
        return accountId != null ? accountService.findOrThrow(accountId) : null;
    }

    public ExpenseCategory findOrThrow(Long id) {
        return expenseCategoryRepository.findById(id)
                .orElseThrow(() -> new MasterNotFoundException("Expense Category", id));
    }

    /** Same as {@link #findOrThrow} but also rejects an inactive category — used when creating/editing an Expense. */
    public ExpenseCategory findActiveOrThrow(Long id) {
        ExpenseCategory category = findOrThrow(id);
        if (!category.isActive()) {
            throw new BadRequestException("Expense category '" + category.getName() + "' is inactive and cannot be used for new expenses");
        }
        return category;
    }
}
