package com.storehub.service;

import com.storehub.dto.SupplierCreateRequest;
import com.storehub.dto.SupplierResponse;
import com.storehub.entity.Supplier;
import com.storehub.exception.SupplierNotFoundException;
import com.storehub.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SupplierService {

    private final SupplierRepository supplierRepository;

    public List<SupplierResponse> getAllSuppliers() {
        return supplierRepository.findAll(Sort.by("name").ascending()).stream()
                .map(SupplierResponse::fromEntity)
                .toList();
    }

    @Transactional
    public SupplierResponse createSupplier(SupplierCreateRequest request) {
        Supplier supplier = Supplier.builder()
                .name(request.getName())
                .phone(request.getPhone())
                .email(request.getEmail())
                .address(request.getAddress())
                .build();

        return SupplierResponse.fromEntity(supplierRepository.save(supplier));
    }

    public Supplier findSupplierOrThrow(Long id) {
        return supplierRepository.findById(id)
                .orElseThrow(() -> new SupplierNotFoundException(id));
    }
}
