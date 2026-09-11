package com.storehub.service;

import com.storehub.dto.AccountGroupResponse;
import com.storehub.dto.AccountRequest;
import com.storehub.dto.AccountResponse;
import com.storehub.dto.PagedResponse;
import com.storehub.entity.Account;
import com.storehub.entity.AccountGroup;
import com.storehub.entity.AccountType;
import com.storehub.entity.LedgerEntryType;
import com.storehub.entity.SystemAccountCode;
import com.storehub.entity.User;
import com.storehub.exception.AccountNotFoundException;
import com.storehub.exception.BadRequestException;
import com.storehub.repository.AccountGroupRepository;
import com.storehub.repository.AccountRepository;
import com.storehub.security.UserPrincipal;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Chart of Accounts: account groups + accounts. Business logic must never
 * hard-code an account id; it resolves the fixed set of accounts the app
 * posts to via {@link #getSystemAccount(SystemAccountCode)}, which reads
 * from the {@code accounts} table by {@code accountCode} (seeded once on
 * startup, idempotently, if missing).
 */
@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final AccountGroupRepository accountGroupRepository;

    /** Small in-memory cache from SystemAccountCode -> Account id, avoids a query per journal line. */
    private final Map<SystemAccountCode, Long> systemAccountIdCache = new ConcurrentHashMap<>();

    @PostConstruct
    @Transactional
    public void ensureSystemAccountsExist() {
        Map<AccountType, AccountGroup> topLevelGroups = new EnumMap<>(AccountType.class);
        for (AccountType type : AccountType.values()) {
            topLevelGroups.put(type, findOrCreateGroup(displayName(type), type, null));
        }

        for (SystemAccountCode code : SystemAccountCode.values()) {
            accountRepository.findByAccountCode(code.getCode()).orElseGet(() -> accountRepository.save(Account.builder()
                    .accountCode(code.getCode())
                    .accountName(code.getDefaultName())
                    .accountType(code.getAccountType())
                    .accountGroup(topLevelGroups.get(code.getAccountType()))
                    .openingBalance(BigDecimal.ZERO)
                    .openingBalanceType(code.getNormalBalance())
                    .systemAccount(true)
                    .active(true)
                    .createdBy("System")
                    .build()));
        }
    }

    private AccountGroup findOrCreateGroup(String name, AccountType type, AccountGroup parent) {
        return accountGroupRepository.findByNameIgnoreCase(name)
                .orElseGet(() -> accountGroupRepository.save(AccountGroup.builder()
                        .name(name)
                        .accountType(type)
                        .parentGroup(parent)
                        .active(true)
                        .build()));
    }

    private String displayName(AccountType type) {
        return switch (type) {
            case ASSET -> "Assets";
            case LIABILITY -> "Liabilities";
            case INCOME -> "Income";
            case EXPENSE -> "Expenses";
        };
    }

    /** Resolves a fixed system account by its stable code. Never call with a raw numeric id. */
    public Account getSystemAccount(SystemAccountCode code) {
        Long cachedId = systemAccountIdCache.get(code);
        if (cachedId != null) {
            return accountRepository.findById(cachedId)
                    .orElseThrow(() -> new AccountNotFoundException(code.getCode()));
        }
        Account account = accountRepository.findByAccountCode(code.getCode())
                .orElseThrow(() -> new AccountNotFoundException(code.getCode()));
        systemAccountIdCache.put(code, account.getId());
        return account;
    }

    public PagedResponse<AccountResponse> search(String search, AccountType accountType, Boolean active, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("accountCode").ascending());
        Page<AccountResponse> result = accountRepository.search(search, accountType, active, pageable)
                .map(AccountResponse::fromEntity);
        return PagedResponse.fromPage(result);
    }

    public AccountResponse getById(Long id) {
        return AccountResponse.fromEntity(findOrThrow(id));
    }

    public List<AccountGroupResponse> listGroups() {
        return accountGroupRepository.findByActiveTrueOrderByNameAsc().stream()
                .map(AccountGroupResponse::fromEntity)
                .toList();
    }

    @Transactional
    public AccountResponse create(AccountRequest request) {
        if (accountRepository.existsByAccountCodeIgnoreCase(request.getAccountCode())) {
            throw new BadRequestException("An account with code '" + request.getAccountCode() + "' already exists");
        }

        Account account = Account.builder()
                .accountCode(request.getAccountCode())
                .accountName(request.getAccountName())
                .accountType(request.getAccountType())
                .accountGroup(resolveGroup(request.getAccountGroupId()))
                .openingBalance(request.getOpeningBalance())
                .openingBalanceType(request.getOpeningBalanceType())
                .active(request.isActive())
                .systemAccount(false)
                .createdBy(currentUsername())
                .build();

        return AccountResponse.fromEntity(accountRepository.save(account));
    }

    @Transactional
    public AccountResponse update(Long id, AccountRequest request) {
        Account account = findOrThrow(id);

        if (!account.getAccountCode().equalsIgnoreCase(request.getAccountCode())) {
            if (account.isSystemAccount()) {
                throw new BadRequestException("The code of a system account cannot be changed");
            }
            if (accountRepository.existsByAccountCodeIgnoreCase(request.getAccountCode())) {
                throw new BadRequestException("An account with code '" + request.getAccountCode() + "' already exists");
            }
            account.setAccountCode(request.getAccountCode());
        }

        account.setAccountName(request.getAccountName());
        if (!account.isSystemAccount()) {
            account.setAccountType(request.getAccountType());
        }
        account.setAccountGroup(resolveGroup(request.getAccountGroupId()));
        account.setOpeningBalance(request.getOpeningBalance());
        account.setOpeningBalanceType(request.getOpeningBalanceType());
        account.setActive(request.isActive());
        account.setModifiedBy(currentUsername());

        return AccountResponse.fromEntity(accountRepository.save(account));
    }

    private AccountGroup resolveGroup(Long groupId) {
        if (groupId == null) {
            return null;
        }
        return accountGroupRepository.findById(groupId)
                .orElseThrow(() -> new BadRequestException("Account group not found: " + groupId));
    }

    public Account findOrThrow(Long id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new AccountNotFoundException(id));
    }

    private String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal) {
            User user = principal.getUser();
            String lastName = user.getLastName() != null ? user.getLastName() : "";
            return (user.getFirstName() + " " + lastName).trim();
        }
        return "System";
    }
}
