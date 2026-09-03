package com.storehub.service;

import com.storehub.dto.ProductCreateRequest;
import com.storehub.dto.ProductResponse;
import com.storehub.entity.Product;
import com.storehub.exception.BadRequestException;
import com.storehub.exception.ProductNotFoundException;
import com.storehub.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    public List<ProductResponse> getAllProducts() {
        return productRepository.findAll(Sort.by("name").ascending()).stream()
                .map(ProductResponse::fromEntity)
                .toList();
    }

    @Transactional
    public ProductResponse createProduct(ProductCreateRequest request) {
        if (productRepository.existsByNameIgnoreCase(request.getName())) {
            throw new BadRequestException("A product named '" + request.getName() + "' already exists");
        }

        Product product = Product.builder()
                .name(request.getName())
                .unit(request.getUnit())
                .build();

        return ProductResponse.fromEntity(productRepository.save(product));
    }

    public Product findProductOrThrow(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
    }
}
