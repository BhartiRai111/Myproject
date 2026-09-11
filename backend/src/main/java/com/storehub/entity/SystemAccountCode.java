package com.storehub.entity;

/**
 * Well-known accounts the application posts to by code, never by raw id.
 * Each constant's {@link #code} is the {@code Account.accountCode} row
 * seeded by {@code AccountSeedService} on startup; business logic resolves
 * an account via {@code AccountService.getSystemAccount(SystemAccountCode)}.
 */
public enum SystemAccountCode {

    CASH("CASH", "Cash", AccountType.ASSET, LedgerEntryType.DEBIT),
    BANK("BANK", "Bank", AccountType.ASSET, LedgerEntryType.DEBIT),
    CUSTOMER_RECEIVABLE("CUST-RECV", "Customer Receivable", AccountType.ASSET, LedgerEntryType.DEBIT),
    INVENTORY("INVENTORY", "Inventory / Stock", AccountType.ASSET, LedgerEntryType.DEBIT),
    INPUT_CGST("IN-CGST", "Input CGST", AccountType.ASSET, LedgerEntryType.DEBIT),
    INPUT_SGST("IN-SGST", "Input SGST", AccountType.ASSET, LedgerEntryType.DEBIT),
    INPUT_IGST("IN-IGST", "Input IGST", AccountType.ASSET, LedgerEntryType.DEBIT),

    SUPPLIER_PAYABLE("SUPP-PAY", "Supplier Payable", AccountType.LIABILITY, LedgerEntryType.CREDIT),
    OUTPUT_CGST("OUT-CGST", "Output CGST", AccountType.LIABILITY, LedgerEntryType.CREDIT),
    OUTPUT_SGST("OUT-SGST", "Output SGST", AccountType.LIABILITY, LedgerEntryType.CREDIT),
    OUTPUT_IGST("OUT-IGST", "Output IGST", AccountType.LIABILITY, LedgerEntryType.CREDIT),

    SALES("SALES", "Sales", AccountType.INCOME, LedgerEntryType.CREDIT),
    OTHER_INCOME("OTHER-INC", "Other Income", AccountType.INCOME, LedgerEntryType.CREDIT),
    DISCOUNT_RECEIVED("DISC-RECV", "Discount Received / Purchase Discount", AccountType.INCOME, LedgerEntryType.CREDIT),

    PURCHASE("PURCHASE", "Purchase", AccountType.EXPENSE, LedgerEntryType.DEBIT),
    EXPENSES("EXPENSES", "Expenses", AccountType.EXPENSE, LedgerEntryType.DEBIT),
    DISCOUNT_ALLOWED("DISC-ALLOW", "Discount Allowed / Sales Discount", AccountType.EXPENSE, LedgerEntryType.DEBIT),
    ROUND_OFF("ROUND-OFF", "Round Off", AccountType.EXPENSE, LedgerEntryType.DEBIT);

    private final String code;
    private final String defaultName;
    private final AccountType accountType;
    private final LedgerEntryType normalBalance;

    SystemAccountCode(String code, String defaultName, AccountType accountType, LedgerEntryType normalBalance) {
        this.code = code;
        this.defaultName = defaultName;
        this.accountType = accountType;
        this.normalBalance = normalBalance;
    }

    public String getCode() {
        return code;
    }

    public String getDefaultName() {
        return defaultName;
    }

    public AccountType getAccountType() {
        return accountType;
    }

    public LedgerEntryType getNormalBalance() {
        return normalBalance;
    }
}
