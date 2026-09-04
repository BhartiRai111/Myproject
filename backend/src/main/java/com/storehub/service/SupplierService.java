package com.storehub.service;

import com.storehub.dto.PagedResponse;
import com.storehub.dto.SupplierCreateRequest;
import com.storehub.dto.SupplierPurchaseSummary;
import com.storehub.dto.SupplierResponse;
import com.storehub.dto.SupplierUpdateRequest;
import com.storehub.entity.Supplier;
import com.storehub.entity.SupplierStatus;
import com.storehub.exception.BadRequestException;
import com.storehub.exception.SupplierNotFoundException;
import com.storehub.repository.PurchaseRepository;
import com.storehub.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SupplierService {

    private static final Set<String> SORTABLE_FIELDS = Set.of("name", "city", "createdAt");

    private final SupplierRepository supplierRepository;
    private final PurchaseRepository purchaseRepository;

    public PagedResponse<SupplierResponse> searchSuppliers(String search, SupplierStatus status,
                                                            int page, int size, String sortBy, String sortDir) {
        String field = SORTABLE_FIELDS.contains(sortBy) ? sortBy : "name";
        Sort sort = "desc".equalsIgnoreCase(sortDir) ? Sort.by(field).descending() : Sort.by(field).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<Supplier> result = supplierRepository.search(search, status, pageable);
        return PagedResponse.fromPage(result.map(SupplierResponse::fromEntity));
    }

    public SupplierResponse getSupplierById(Long id) {
        return SupplierResponse.fromEntity(findSupplierOrThrow(id));
    }

    public List<SupplierPurchaseSummary> getPurchaseHistory(Long supplierId) {
        findSupplierOrThrow(supplierId);
        return purchaseRepository.findBySupplierIdOrderByCreatedAtDesc(supplierId).stream()
                .map(SupplierPurchaseSummary::fromEntity)
                .toList();
    }

    @Transactional
    public SupplierResponse createSupplier(SupplierCreateRequest request) {
        if (request.getEmail() != null && !request.getEmail().isBlank()
                && supplierRepository.existsByEmailIgnoreCase(request.getEmail())) {
            throw new BadRequestException("A supplier with email '" + request.getEmail() + "' already exists");
        }
        if (request.getGstNumber() != null && !request.getGstNumber().isBlank()
                && supplierRepository.existsByGstNumberIgnoreCase(request.getGstNumber())) {
            throw new BadRequestException("A supplier with GST number '" + request.getGstNumber() + "' already exists");
        }

        Supplier supplier = Supplier.builder()
                .name(request.getName())
                .contactPerson(request.getContactPerson())
                .mobile(request.getMobile())
                .email(blankToNull(request.getEmail()))
                .address(request.getAddress())
                .city(request.getCity())
                .state(request.getState())
                .pincode(request.getPincode())
                .gstNumber(blankToNull(request.getGstNumber()))
                .notes(request.getNotes())
                .build();

        return SupplierResponse.fromEntity(supplierRepository.save(supplier));
    }

    @Transactional
    public SupplierResponse updateSupplier(Long id, SupplierUpdateRequest request) {
        Supplier supplier = findSupplierOrThrow(id);

        String newEmail = blankToNull(request.getEmail());
        if (newEmail != null && !newEmail.equalsIgnoreCase(supplier.getEmail())
                && supplierRepository.existsByEmailIgnoreCaseAndIdNot(newEmail, id)) {
            throw new BadRequestException("A supplier with email '" + newEmail + "' already exists");
        }
        String newGstNumber = blankToNull(request.getGstNumber());
        if (newGstNumber != null && !newGstNumber.equalsIgnoreCase(supplier.getGstNumber())
                && supplierRepository.existsByGstNumberIgnoreCaseAndIdNot(newGstNumber, id)) {
            throw new BadRequestException("A supplier with GST number '" + newGstNumber + "' already exists");
        }

        supplier.setName(request.getName());
        supplier.setContactPerson(request.getContactPerson());
        supplier.setMobile(request.getMobile());
        supplier.setEmail(newEmail);
        supplier.setAddress(request.getAddress());
        supplier.setCity(request.getCity());
        supplier.setState(request.getState());
        supplier.setPincode(request.getPincode());
        supplier.setGstNumber(newGstNumber);
        supplier.setNotes(request.getNotes());

        return SupplierResponse.fromEntity(supplierRepository.save(supplier));
    }

    @Transactional
    public SupplierResponse setStatus(Long id, SupplierStatus status) {
        Supplier supplier = findSupplierOrThrow(id);
        supplier.setStatus(status);
        return SupplierResponse.fromEntity(supplierRepository.save(supplier));
    }

    public Supplier findSupplierOrThrow(Long id) {
        return supplierRepository.findById(id)
                .orElseThrow(() -> new SupplierNotFoundException(id));
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
