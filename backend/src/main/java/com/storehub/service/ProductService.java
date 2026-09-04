package com.storehub.service;

import com.storehub.dto.PagedResponse;
import com.storehub.dto.ProductCreateRequest;
import com.storehub.dto.ProductResponse;
import com.storehub.dto.ProductUpdateRequest;
import com.storehub.entity.Category;
import com.storehub.entity.Product;
import com.storehub.entity.ProductStatus;
import com.storehub.exception.BadRequestException;
import com.storehub.exception.ProductNotFoundException;
import com.storehub.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ProductService {

    private static final Set<String> SORTABLE_FIELDS = Set.of(
            "name", "sku", "purchasePrice", "sellingPrice", "createdAt");

    private final ProductRepository productRepository;
    private final CategoryService categoryService;
    private final InventoryService inventoryService;

    public PagedResponse<ProductResponse> searchProducts(String search, Long categoryId, ProductStatus status,
                                                           int page, int size, String sortBy, String sortDir) {
        String field = SORTABLE_FIELDS.contains(sortBy) ? sortBy : "name";
        Sort sort = "desc".equalsIgnoreCase(sortDir) ? Sort.by(field).descending() : Sort.by(field).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<Product> result = productRepository.search(search, categoryId, status, pageable);

        List<Long> productIds = result.getContent().stream().map(Product::getId).toList();
        Map<Long, Integer> stockByProductId = inventoryService.getCurrentStockBulk(productIds);

        return PagedResponse.fromPage(result.map(product ->
                ProductResponse.fromEntity(product, stockByProductId.getOrDefault(product.getId(), 0))));
    }

    public ProductResponse getProductById(Long id) {
        Product product = findProductOrThrow(id);
        return ProductResponse.fromEntity(product, inventoryService.getCurrentStock(id));
    }

    @Transactional
    public ProductResponse createProduct(ProductCreateRequest request) {
        if (productRepository.existsByNameIgnoreCase(request.getName())) {
            throw new BadRequestException("A product named '" + request.getName() + "' already exists");
        }
        if (productRepository.existsBySkuIgnoreCase(request.getSku())) {
            throw new BadRequestException("A product with SKU '" + request.getSku() + "' already exists");
        }
        if (request.getBarcode() != null && !request.getBarcode().isBlank()
                && productRepository.existsByBarcodeIgnoreCase(request.getBarcode())) {
            throw new BadRequestException("A product with barcode '" + request.getBarcode() + "' already exists");
        }

        Category category = categoryService.findCategoryOrThrow(request.getCategoryId());

        Product product = Product.builder()
                .name(request.getName())
                .sku(request.getSku())
                .barcode(blankToNull(request.getBarcode()))
                .category(category)
                .brand(request.getBrand())
                .unit(request.getUnit())
                .purchasePrice(request.getPurchasePrice())
                .sellingPrice(request.getSellingPrice())
                .tax(request.getTax())
                .minStockLevel(request.getMinStockLevel())
                .description(request.getDescription())
                .build();

        Product saved = productRepository.save(product);
        inventoryService.createInventoryForProduct(saved);

        return ProductResponse.fromEntity(saved, 0);
    }

    @Transactional
    public ProductResponse updateProduct(Long id, ProductUpdateRequest request) {
        Product product = findProductOrThrow(id);

        if (!product.getName().equalsIgnoreCase(request.getName())
                && productRepository.existsByNameIgnoreCase(request.getName())) {
            throw new BadRequestException("A product named '" + request.getName() + "' already exists");
        }
        if (!product.getSku().equalsIgnoreCase(request.getSku())
                && productRepository.existsBySkuIgnoreCaseAndIdNot(request.getSku(), id)) {
            throw new BadRequestException("A product with SKU '" + request.getSku() + "' already exists");
        }
        String newBarcode = blankToNull(request.getBarcode());
        if (newBarcode != null && !newBarcode.equalsIgnoreCase(product.getBarcode())
                && productRepository.existsByBarcodeIgnoreCaseAndIdNot(newBarcode, id)) {
            throw new BadRequestException("A product with barcode '" + newBarcode + "' already exists");
        }

        Category category = categoryService.findCategoryOrThrow(request.getCategoryId());

        product.setName(request.getName());
        product.setSku(request.getSku());
        product.setBarcode(newBarcode);
        product.setCategory(category);
        product.setBrand(request.getBrand());
        product.setUnit(request.getUnit());
        product.setPurchasePrice(request.getPurchasePrice());
        product.setSellingPrice(request.getSellingPrice());
        product.setTax(request.getTax());
        product.setMinStockLevel(request.getMinStockLevel());
        product.setDescription(request.getDescription());

        Product saved = productRepository.save(product);
        return ProductResponse.fromEntity(saved, inventoryService.getCurrentStock(id));
    }

    @Transactional
    public ProductResponse setStatus(Long id, ProductStatus status) {
        Product product = findProductOrThrow(id);
        product.setStatus(status);
        Product saved = productRepository.save(product);
        return ProductResponse.fromEntity(saved, inventoryService.getCurrentStock(id));
    }

    public Product findProductOrThrow(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
