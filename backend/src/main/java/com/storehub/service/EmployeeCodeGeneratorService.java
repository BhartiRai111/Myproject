package com.storehub.service;

import com.storehub.entity.EmployeeCodeSequence;
import com.storehub.repository.EmployeeCodeSequenceRepository;
import com.storehub.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The ONLY place an auto-generated employee code is produced (StoreHub Employee/User
 * Roles spec section 5). Format: {@code EMP-000001}. Manually-entered codes bypass this
 * entirely and go straight through {@code EmployeeService}'s uniqueness check — mirrors
 * {@code SkuGeneratorService} exactly, including running in its own {@code REQUIRES_NEW}
 * transaction so the single counter row is locked/released independently of the caller's
 * transaction and two simultaneous "Generate Code" calls can never collide. A generated
 * code is never reused even if the caller never saves an employee with it (gaps are fine,
 * duplicates are not).
 */
@Service
@RequiredArgsConstructor
public class EmployeeCodeGeneratorService {

    private static final Long SEQUENCE_ID = 1L;
    private static final String PREFIX = "EMP-";

    private final EmployeeCodeSequenceRepository sequenceRepository;
    private final EmployeeRepository employeeRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String generateNext() {
        sequenceRepository.ensureRowExists(SEQUENCE_ID);
        EmployeeCodeSequence seq = sequenceRepository.lockForUpdate(SEQUENCE_ID)
                .orElseThrow(() -> new IllegalStateException("Employee code sequence row missing after ensureRowExists"));

        String candidate;
        do {
            seq.setLastNumber(seq.getLastNumber() + 1);
            candidate = format(seq.getLastNumber());
            // Defensive: guards against a manually-entered code that happens to already
            // match the generated pattern (e.g. someone typed "EMP-000005" by hand).
        } while (employeeRepository.existsByEmployeeCodeIgnoreCase(candidate));

        sequenceRepository.save(seq);
        return candidate;
    }

    private String format(long number) {
        return PREFIX + String.format("%06d", number);
    }
}
