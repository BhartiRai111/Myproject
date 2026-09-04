package com.storehub.service;

import com.storehub.dto.CityRequest;
import com.storehub.dto.CityResponse;
import com.storehub.dto.PagedResponse;
import com.storehub.entity.City;
import com.storehub.entity.CityStatus;
import com.storehub.entity.State;
import com.storehub.entity.StateStatus;
import com.storehub.exception.BadRequestException;
import com.storehub.exception.MasterNotFoundException;
import com.storehub.repository.CityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CityService {

    private final CityRepository cityRepository;
    private final StateService stateService;

    public PagedResponse<CityResponse> search(String search, Long stateId, Long countryId, CityStatus status,
                                               int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("name").ascending());
        Page<City> result = cityRepository.search(search, stateId, countryId, status, pageable);
        return PagedResponse.fromPage(result.map(CityResponse::fromEntity));
    }

    public CityResponse getById(Long id) {
        return CityResponse.fromEntity(findOrThrow(id));
    }

    @Transactional
    public CityResponse create(CityRequest request) {
        State state = stateService.findOrThrow(request.getStateId());
        if (state.getStatus() == StateStatus.INACTIVE) {
            throw new BadRequestException("State '" + state.getName() + "' is inactive and cannot be used for a new city");
        }
        if (cityRepository.existsByNameIgnoreCaseAndStateId(request.getName(), state.getId())) {
            throw new BadRequestException("A city named '" + request.getName() + "' already exists under " + state.getName());
        }

        City city = City.builder()
                .name(request.getName())
                .state(state)
                .build();

        return CityResponse.fromEntity(cityRepository.save(city));
    }

    @Transactional
    public CityResponse update(Long id, CityRequest request) {
        City city = findOrThrow(id);
        Long oldStateId = city.getState().getId();

        State state = stateService.findOrThrow(request.getStateId());
        if (state.getStatus() == StateStatus.INACTIVE && !state.getId().equals(oldStateId)) {
            throw new BadRequestException("State '" + state.getName() + "' is inactive and cannot be used for a city");
        }
        if ((!city.getName().equalsIgnoreCase(request.getName()) || !state.getId().equals(oldStateId))
                && cityRepository.existsByNameIgnoreCaseAndStateIdAndIdNot(request.getName(), state.getId(), id)) {
            throw new BadRequestException("A city named '" + request.getName() + "' already exists under " + state.getName());
        }

        city.setName(request.getName());
        city.setState(state);

        return CityResponse.fromEntity(cityRepository.save(city));
    }

    @Transactional
    public CityResponse setStatus(Long id, CityStatus status) {
        City city = findOrThrow(id);
        city.setStatus(status);
        return CityResponse.fromEntity(cityRepository.save(city));
    }

    public City findOrThrow(Long id) {
        return cityRepository.findById(id)
                .orElseThrow(() -> new MasterNotFoundException("City", id));
    }
}
