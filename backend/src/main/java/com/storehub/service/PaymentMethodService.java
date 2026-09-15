package com.storehub.service;

import com.storehub.dto.PaymentMethodRequest;
import com.storehub.dto.PaymentMethodResponse;
import com.storehub.entity.PaymentMethod;
import com.storehub.exception.BadRequestException;
import com.storehub.exception.MasterNotFoundException;
import com.storehub.repository.PaymentMethodRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PaymentMethodService {

    private final PaymentMethodRepository paymentMethodRepository;

    public List<PaymentMethodResponse> listAll() {
        return paymentMethodRepository.findAllByOrderBySortOrderAscNameAsc().stream()
                .map(PaymentMethodResponse::fromEntity).toList();
    }

    /** Active methods only — what should populate a payment-mode dropdown on a new transaction. */
    public List<PaymentMethodResponse> listActive() {
        return paymentMethodRepository.findByActiveTrueOrderBySortOrderAscNameAsc().stream()
                .map(PaymentMethodResponse::fromEntity).toList();
    }

    @Transactional
    public PaymentMethodResponse create(PaymentMethodRequest request) {
        if (paymentMethodRepository.existsByNameIgnoreCase(request.getName())) {
            throw new BadRequestException("A payment method named '" + request.getName() + "' already exists");
        }
        PaymentMethod method = PaymentMethod.builder()
                .name(request.getName())
                .type(request.getType())
                .sortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0)
                .active(true)
                .build();
        return PaymentMethodResponse.fromEntity(paymentMethodRepository.save(method));
    }

    @Transactional
    public PaymentMethodResponse update(Long id, PaymentMethodRequest request) {
        PaymentMethod method = findOrThrow(id);
        if (!method.getName().equalsIgnoreCase(request.getName())
                && paymentMethodRepository.existsByNameIgnoreCaseAndIdNot(request.getName(), id)) {
            throw new BadRequestException("A payment method named '" + request.getName() + "' already exists");
        }
        method.setName(request.getName());
        method.setType(request.getType());
        method.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : method.getSortOrder());
        return PaymentMethodResponse.fromEntity(paymentMethodRepository.save(method));
    }

    /** Never a hard delete (spec section 15): a payment method already referenced by historical transactions must stay visible on them. */
    @Transactional
    public PaymentMethodResponse setActive(Long id, boolean active) {
        PaymentMethod method = findOrThrow(id);
        method.setActive(active);
        return PaymentMethodResponse.fromEntity(paymentMethodRepository.save(method));
    }

    private PaymentMethod findOrThrow(Long id) {
        return paymentMethodRepository.findById(id)
                .orElseThrow(() -> new MasterNotFoundException("Payment Method", id));
    }
}
