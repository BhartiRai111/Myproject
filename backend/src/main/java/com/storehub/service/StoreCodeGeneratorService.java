package com.storehub.service;

import com.storehub.entity.StoreCodeSequence;
import com.storehub.repository.StoreCodeSequenceRepository;
import com.storehub.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The ONLY place an auto-generated store code is produced (Multi-Store spec section 5).
 * Format: {@code STR-000001}. Mirrors {@code EmployeeCodeGeneratorService} exactly,
 * including running in its own {@code REQUIRES_NEW} transaction so the single counter
 * row is locked/released independently of the caller's transaction and two simultaneous
 * "Generate Code" calls can never collide.
 */
@Service
@RequiredArgsConstructor
public class StoreCodeGeneratorService {

    private static final Long SEQUENCE_ID = 1L;
    private static final String PREFIX = "STR-";

    private final StoreCodeSequenceRepository sequenceRepository;
    private final StoreRepository storeRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String generateNext() {
        sequenceRepository.ensureRowExists(SEQUENCE_ID);
        StoreCodeSequence seq = sequenceRepository.lockForUpdate(SEQUENCE_ID)
                .orElseThrow(() -> new IllegalStateException("Store code sequence row missing after ensureRowExists"));

        String candidate;
        do {
            seq.setLastNumber(seq.getLastNumber() + 1);
            candidate = format(seq.getLastNumber());
        } while (storeRepository.existsByStoreCodeIgnoreCase(candidate));

        sequenceRepository.save(seq);
        return candidate;
    }

    private String format(long number) {
        return PREFIX + String.format("%06d", number);
    }
}
