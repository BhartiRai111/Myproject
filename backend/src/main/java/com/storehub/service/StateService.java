package com.storehub.service;

import com.storehub.dto.PagedResponse;
import com.storehub.dto.StateRequest;
import com.storehub.dto.StateResponse;
import com.storehub.entity.Country;
import com.storehub.entity.CountryStatus;
import com.storehub.entity.State;
import com.storehub.entity.StateStatus;
import com.storehub.exception.BadRequestException;
import com.storehub.exception.MasterNotFoundException;
import com.storehub.repository.StateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StateService {

    private final StateRepository stateRepository;
    private final CountryService countryService;

    public PagedResponse<StateResponse> search(String search, Long countryId, StateStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("name").ascending());
        Page<State> result = stateRepository.search(search, countryId, status, pageable);
        return PagedResponse.fromPage(result.map(StateResponse::fromEntity));
    }

    public StateResponse getById(Long id) {
        return StateResponse.fromEntity(findOrThrow(id));
    }

    @Transactional
    public StateResponse create(StateRequest request) {
        Country country = countryService.findOrThrow(request.getCountryId());
        if (country.getStatus() == CountryStatus.INACTIVE) {
            throw new BadRequestException("Country '" + country.getName() + "' is inactive and cannot be used for a new state");
        }
        if (stateRepository.existsByNameIgnoreCaseAndCountryId(request.getName(), country.getId())) {
            throw new BadRequestException("A state named '" + request.getName() + "' already exists under " + country.getName());
        }

        State state = State.builder()
                .name(request.getName())
                .code(request.getCode())
                .country(country)
                .build();

        return StateResponse.fromEntity(stateRepository.save(state));
    }

    @Transactional
    public StateResponse update(Long id, StateRequest request) {
        State state = findOrThrow(id);
        Long oldCountryId = state.getCountry().getId();

        Country country = countryService.findOrThrow(request.getCountryId());
        if (country.getStatus() == CountryStatus.INACTIVE && !country.getId().equals(oldCountryId)) {
            throw new BadRequestException("Country '" + country.getName() + "' is inactive and cannot be used for a state");
        }
        if ((!state.getName().equalsIgnoreCase(request.getName()) || !country.getId().equals(oldCountryId))
                && stateRepository.existsByNameIgnoreCaseAndCountryIdAndIdNot(request.getName(), country.getId(), id)) {
            throw new BadRequestException("A state named '" + request.getName() + "' already exists under " + country.getName());
        }

        state.setName(request.getName());
        state.setCode(request.getCode());
        state.setCountry(country);

        return StateResponse.fromEntity(stateRepository.save(state));
    }

    @Transactional
    public StateResponse setStatus(Long id, StateStatus status) {
        State state = findOrThrow(id);
        state.setStatus(status);
        return StateResponse.fromEntity(stateRepository.save(state));
    }

    public State findOrThrow(Long id) {
        return stateRepository.findById(id)
                .orElseThrow(() -> new MasterNotFoundException("State", id));
    }
}
