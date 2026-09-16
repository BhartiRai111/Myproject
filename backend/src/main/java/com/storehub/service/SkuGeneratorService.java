package com.storehub.service;

import com.storehub.entity.ItemSkuSequence;
import com.storehub.repository.ItemSkuSequenceRepository;
import com.storehub.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The ONLY place an auto-generated SKU is produced (StoreHub SKU Management
 * spec section 8) — manually-entered SKUs bypass this entirely and go
 * straight through {@code ProductService}'s normalization/uniqueness checks.
 * Format: {@code ITEM-000001}.
 *
 * <p>Concurrency: runs in its own {@code REQUIRES_NEW} transaction (mirrors
 * {@link VoucherNumberService}) so the single counter row is locked and
 * released independently of the caller's transaction, and two simultaneous
 * "Generate SKU" calls can never receive the same number.
 *
 * <p>A generated number is never reused even if the caller never actually
 * saves a product with it (same "allocate at generation time, gaps are fine,
 * duplicates are not" policy as voucher numbering) — this is what makes
 * concurrent generation safe.
 */
@Service
@RequiredArgsConstructor
public class SkuGeneratorService {

    private static final Long SEQUENCE_ID = 1L;
    private static final String PREFIX = "ITEM-";

    private final ItemSkuSequenceRepository itemSkuSequenceRepository;
    private final ProductRepository productRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String generateNext() {
        itemSkuSequenceRepository.ensureRowExists(SEQUENCE_ID);
        ItemSkuSequence seq = itemSkuSequenceRepository.lockForUpdate(SEQUENCE_ID)
                .orElseThrow(() -> new IllegalStateException("Item SKU sequence row missing after ensureRowExists"));

        String candidate;
        do {
            seq.setLastNumber(seq.getLastNumber() + 1);
            candidate = format(seq.getLastNumber());
            // Defensive: guards against a manually-entered SKU that happens to already
            // match the generated pattern (e.g. someone typed "ITEM-000005" by hand).
        } while (productRepository.existsBySkuIgnoreCase(candidate));

        itemSkuSequenceRepository.save(seq);
        return candidate;
    }

    private String format(long number) {
        return PREFIX + String.format("%06d", number);
    }
}
