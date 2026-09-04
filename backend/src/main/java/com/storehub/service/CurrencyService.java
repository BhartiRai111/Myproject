package com.storehub.service;

import com.storehub.dto.CurrencyRequest;
import com.storehub.dto.CurrencyResponse;
import com.storehub.dto.PagedResponse;
import com.storehub.entity.Currency;
import com.storehub.entity.CurrencyStatus;
import com.storehub.exception.BadRequestException;
import com.storehub.exception.MasterNotFoundException;
import com.storehub.repository.CurrencyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CurrencyService {

    private final CurrencyRepository currencyRepository;

    public PagedResponse<CurrencyResponse> search(String search, CurrencyStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("name").ascending());
        Page<Currency> result = currencyRepository.search(search, status, pageable);
        return PagedResponse.fromPage(result.map(CurrencyResponse::fromEntity));
    }

    public CurrencyResponse getById(Long id) {
        return CurrencyResponse.fromEntity(findOrThrow(id));
    }

    @Transactional
    public CurrencyResponse create(CurrencyRequest request) {
        if (currencyRepository.existsByNameIgnoreCase(request.getName())) {
            throw new BadRequestException("A currency named '" + request.getName() + "' already exists");
        }
        if (currencyRepository.existsByCodeIgnoreCase(request.getCode())) {
            throw new BadRequestException("A currency with code '" + request.getCode() + "' already exists");
        }

        Currency currency = Currency.builder()
                .name(request.getName())
                .code(request.getCode())
                .symbol(request.getSymbol())
                .decimalPlaces(request.getDecimalPlaces())
                .build();

        return CurrencyResponse.fromEntity(currencyRepository.save(currency));
    }

    @Transactional
    public CurrencyResponse update(Long id, CurrencyRequest request) {
        Currency currency = findOrThrow(id);

        if (!currency.getName().equalsIgnoreCase(request.getName())
                && currencyRepository.existsByNameIgnoreCaseAndIdNot(request.getName(), id)) {
            throw new BadRequestException("A currency named '" + request.getName() + "' already exists");
        }
        if (!currency.getCode().equalsIgnoreCase(request.getCode())
                && currencyRepository.existsByCodeIgnoreCaseAndIdNot(request.getCode(), id)) {
            throw new BadRequestException("A currency with code '" + request.getCode() + "' already exists");
        }

        currency.setName(request.getName());
        currency.setCode(request.getCode());
        currency.setSymbol(request.getSymbol());
        currency.setDecimalPlaces(request.getDecimalPlaces());

        return CurrencyResponse.fromEntity(currencyRepository.save(currency));
    }

    @Transactional
    public CurrencyResponse setStatus(Long id, CurrencyStatus status) {
        Currency currency = findOrThrow(id);
        currency.setStatus(status);
        return CurrencyResponse.fromEntity(currencyRepository.save(currency));
    }

    private Currency findOrThrow(Long id) {
        return currencyRepository.findById(id)
                .orElseThrow(() -> new MasterNotFoundException("Currency", id));
    }
}
