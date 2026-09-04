package com.storehub.service;

import com.storehub.dto.PagedResponse;
import com.storehub.dto.ZoneRequest;
import com.storehub.dto.ZoneResponse;
import com.storehub.entity.Country;
import com.storehub.entity.State;
import com.storehub.entity.Zone;
import com.storehub.entity.ZoneStatus;
import com.storehub.exception.BadRequestException;
import com.storehub.exception.MasterNotFoundException;
import com.storehub.repository.ZoneRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ZoneService {

    private final ZoneRepository zoneRepository;
    private final CountryService countryService;
    private final StateService stateService;

    public PagedResponse<ZoneResponse> search(String search, ZoneStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("name").ascending());
        Page<Zone> result = zoneRepository.search(search, status, pageable);
        return PagedResponse.fromPage(result.map(ZoneResponse::fromEntity));
    }

    public ZoneResponse getById(Long id) {
        return ZoneResponse.fromEntity(findOrThrow(id));
    }

    @Transactional
    public ZoneResponse create(ZoneRequest request) {
        if (zoneRepository.existsByNameIgnoreCase(request.getName())) {
            throw new BadRequestException("A zone named '" + request.getName() + "' already exists");
        }

        Country country = request.getCountryId() != null ? countryService.findOrThrow(request.getCountryId()) : null;
        State state = request.getStateId() != null ? stateService.findOrThrow(request.getStateId()) : null;

        Zone zone = Zone.builder()
                .name(request.getName())
                .code(request.getCode())
                .country(country)
                .state(state)
                .build();

        return ZoneResponse.fromEntity(zoneRepository.save(zone));
    }

    @Transactional
    public ZoneResponse update(Long id, ZoneRequest request) {
        Zone zone = findOrThrow(id);

        if (!zone.getName().equalsIgnoreCase(request.getName())
                && zoneRepository.existsByNameIgnoreCaseAndIdNot(request.getName(), id)) {
            throw new BadRequestException("A zone named '" + request.getName() + "' already exists");
        }

        Country country = request.getCountryId() != null ? countryService.findOrThrow(request.getCountryId()) : null;
        State state = request.getStateId() != null ? stateService.findOrThrow(request.getStateId()) : null;

        zone.setName(request.getName());
        zone.setCode(request.getCode());
        zone.setCountry(country);
        zone.setState(state);

        return ZoneResponse.fromEntity(zoneRepository.save(zone));
    }

    @Transactional
    public ZoneResponse setStatus(Long id, ZoneStatus status) {
        Zone zone = findOrThrow(id);
        zone.setStatus(status);
        return ZoneResponse.fromEntity(zoneRepository.save(zone));
    }

    public Zone findOrThrow(Long id) {
        return zoneRepository.findById(id)
                .orElseThrow(() -> new MasterNotFoundException("Zone", id));
    }
}
