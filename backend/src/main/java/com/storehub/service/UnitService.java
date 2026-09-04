package com.storehub.service;

import com.storehub.dto.PagedResponse;
import com.storehub.dto.UnitRequest;
import com.storehub.dto.UnitResponse;
import com.storehub.entity.Unit;
import com.storehub.entity.UnitStatus;
import com.storehub.exception.BadRequestException;
import com.storehub.exception.MasterNotFoundException;
import com.storehub.repository.UnitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UnitService {

    private final UnitRepository unitRepository;

    public PagedResponse<UnitResponse> search(String search, UnitStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("name").ascending());
        Page<Unit> result = unitRepository.search(search, status, pageable);
        return PagedResponse.fromPage(result.map(UnitResponse::fromEntity));
    }

    public UnitResponse getById(Long id) {
        return UnitResponse.fromEntity(findOrThrow(id));
    }

    @Transactional
    public UnitResponse create(UnitRequest request) {
        if (unitRepository.existsByNameIgnoreCase(request.getName())) {
            throw new BadRequestException("A unit named '" + request.getName() + "' already exists");
        }
        if (unitRepository.existsBySymbolIgnoreCase(request.getSymbol())) {
            throw new BadRequestException("A unit with symbol '" + request.getSymbol() + "' already exists");
        }

        Unit unit = Unit.builder()
                .name(request.getName())
                .symbol(request.getSymbol())
                .build();

        return UnitResponse.fromEntity(unitRepository.save(unit));
    }

    @Transactional
    public UnitResponse update(Long id, UnitRequest request) {
        Unit unit = findOrThrow(id);

        if (!unit.getName().equalsIgnoreCase(request.getName())
                && unitRepository.existsByNameIgnoreCaseAndIdNot(request.getName(), id)) {
            throw new BadRequestException("A unit named '" + request.getName() + "' already exists");
        }
        if (!unit.getSymbol().equalsIgnoreCase(request.getSymbol())
                && unitRepository.existsBySymbolIgnoreCaseAndIdNot(request.getSymbol(), id)) {
            throw new BadRequestException("A unit with symbol '" + request.getSymbol() + "' already exists");
        }

        unit.setName(request.getName());
        unit.setSymbol(request.getSymbol());

        return UnitResponse.fromEntity(unitRepository.save(unit));
    }

    @Transactional
    public UnitResponse setStatus(Long id, UnitStatus status) {
        Unit unit = findOrThrow(id);
        unit.setStatus(status);
        return UnitResponse.fromEntity(unitRepository.save(unit));
    }

    public Unit findOrThrow(Long id) {
        return unitRepository.findById(id)
                .orElseThrow(() -> new MasterNotFoundException("Unit", id));
    }
}
