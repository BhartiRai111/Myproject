package com.storehub.service;

import com.storehub.dto.CategoryCreateRequest;
import com.storehub.dto.CategoryResponse;
import com.storehub.dto.CategoryUpdateRequest;
import com.storehub.entity.Category;
import com.storehub.entity.CategoryStatus;
import com.storehub.exception.BadRequestException;
import com.storehub.exception.CategoryNotFoundException;
import com.storehub.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public List<CategoryResponse> getAllCategories() {
        return categoryRepository.findAll(Sort.by("name").ascending()).stream()
                .map(CategoryResponse::fromEntity)
                .toList();
    }

    public CategoryResponse getCategoryById(Long id) {
        return CategoryResponse.fromEntity(findCategoryOrThrow(id));
    }

    @Transactional
    public CategoryResponse createCategory(CategoryCreateRequest request) {
        if (categoryRepository.existsByNameIgnoreCase(request.getName())) {
            throw new BadRequestException("A category named '" + request.getName() + "' already exists");
        }

        Category category = Category.builder()
                .name(request.getName())
                .description(request.getDescription())
                .itemType(request.getItemType())
                .applicableProperty(request.getApplicableProperty())
                .build();

        return CategoryResponse.fromEntity(categoryRepository.save(category));
    }

    @Transactional
    public CategoryResponse updateCategory(Long id, CategoryUpdateRequest request) {
        Category category = findCategoryOrThrow(id);

        if (!category.getName().equalsIgnoreCase(request.getName())
                && categoryRepository.existsByNameIgnoreCaseAndIdNot(request.getName(), id)) {
            throw new BadRequestException("A category named '" + request.getName() + "' already exists");
        }

        category.setName(request.getName());
        category.setDescription(request.getDescription());
        category.setItemType(request.getItemType());
        category.setApplicableProperty(request.getApplicableProperty());

        return CategoryResponse.fromEntity(categoryRepository.save(category));
    }

    @Transactional
    public CategoryResponse setStatus(Long id, CategoryStatus status) {
        Category category = findCategoryOrThrow(id);
        category.setStatus(status);
        return CategoryResponse.fromEntity(categoryRepository.save(category));
    }

    public Category findCategoryOrThrow(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new CategoryNotFoundException(id));
    }
}
