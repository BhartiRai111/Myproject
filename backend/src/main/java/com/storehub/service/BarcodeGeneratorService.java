package com.storehub.service;

import com.storehub.entity.ItemBarcodeSequence;
import com.storehub.repository.ItemBarcodeSequenceRepository;
import com.storehub.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The ONLY place an internal barcode is produced (Barcode Management spec
 * sections 13/14) — for items that have no official retail barcode of their
 * own. Format: {@code INT-000001}, clearly distinct from a real EAN/UPC so
 * it is never mistaken for a manufacturer-assigned code. Manual entry of a
 * real retail barcode remains the default path; this is purely an opt-in
 * fallback.
 *
 * <p>Concurrency: mirrors {@link SkuGeneratorService} exactly — its own
 * {@code REQUIRES_NEW} transaction, a pessimistic lock on a single counter
 * row, and a generated number is never reused even if unused.
 */
@Service
@RequiredArgsConstructor
public class BarcodeGeneratorService {

    private static final Long SEQUENCE_ID = 1L;
    private static final String PREFIX = "INT-";

    private final ItemBarcodeSequenceRepository itemBarcodeSequenceRepository;
    private final ProductRepository productRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String generateNext() {
        itemBarcodeSequenceRepository.ensureRowExists(SEQUENCE_ID);
        ItemBarcodeSequence seq = itemBarcodeSequenceRepository.lockForUpdate(SEQUENCE_ID)
                .orElseThrow(() -> new IllegalStateException("Item barcode sequence row missing after ensureRowExists"));

        String candidate;
        do {
            seq.setLastNumber(seq.getLastNumber() + 1);
            candidate = PREFIX + String.format("%06d", seq.getLastNumber());
        } while (productRepository.existsByBarcodeIgnoreCase(candidate));

        itemBarcodeSequenceRepository.save(seq);
        return candidate;
    }
}
