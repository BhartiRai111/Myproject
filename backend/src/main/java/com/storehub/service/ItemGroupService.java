package com.storehub.service;

import com.storehub.dto.ItemGroupRequest;
import com.storehub.dto.ItemGroupResponse;
import com.storehub.dto.PagedResponse;
import com.storehub.entity.ItemGroup;
import com.storehub.entity.ItemGroupStatus;
import com.storehub.exception.BadRequestException;
import com.storehub.exception.MasterNotFoundException;
import com.storehub.repository.ItemGroupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ItemGroupService {

    private final ItemGroupRepository itemGroupRepository;

    public PagedResponse<ItemGroupResponse> search(String search, ItemGroupStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("name").ascending());
        Page<ItemGroup> result = itemGroupRepository.search(search, status, pageable);
        return PagedResponse.fromPage(result.map(ItemGroupResponse::fromEntity));
    }

    public ItemGroupResponse getById(Long id) {
        return ItemGroupResponse.fromEntity(findOrThrow(id));
    }

    @Transactional
    public ItemGroupResponse create(ItemGroupRequest request) {
        if (itemGroupRepository.existsByNameIgnoreCase(request.getName())) {
            throw new BadRequestException("An item group named '" + request.getName() + "' already exists");
        }

        ItemGroup itemGroup = ItemGroup.builder()
                .name(request.getName())
                .description(request.getDescription())
                .build();

        return ItemGroupResponse.fromEntity(itemGroupRepository.save(itemGroup));
    }

    @Transactional
    public ItemGroupResponse update(Long id, ItemGroupRequest request) {
        ItemGroup itemGroup = findOrThrow(id);

        if (!itemGroup.getName().equalsIgnoreCase(request.getName())
                && itemGroupRepository.existsByNameIgnoreCaseAndIdNot(request.getName(), id)) {
            throw new BadRequestException("An item group named '" + request.getName() + "' already exists");
        }

        itemGroup.setName(request.getName());
        itemGroup.setDescription(request.getDescription());

        return ItemGroupResponse.fromEntity(itemGroupRepository.save(itemGroup));
    }

    @Transactional
    public ItemGroupResponse setStatus(Long id, ItemGroupStatus status) {
        ItemGroup itemGroup = findOrThrow(id);
        itemGroup.setStatus(status);
        return ItemGroupResponse.fromEntity(itemGroupRepository.save(itemGroup));
    }

    public ItemGroup findOrThrow(Long id) {
        return itemGroupRepository.findById(id)
                .orElseThrow(() -> new MasterNotFoundException("Item Group", id));
    }
}
