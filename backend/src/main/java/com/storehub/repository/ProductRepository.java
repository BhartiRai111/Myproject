package com.storehub.repository;

import com.storehub.entity.Product;
import com.storehub.entity.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {

    boolean existsByNameIgnoreCase(String name);

    boolean existsBySkuIgnoreCase(String sku);

    boolean existsBySkuIgnoreCaseAndIdNot(String sku, Long id);

    boolean existsByBarcodeIgnoreCase(String barcode);

    boolean existsByBarcodeIgnoreCaseAndIdNot(String barcode, Long id);

    @Query("SELECT p FROM Product p WHERE " +
            "(:search IS NULL OR :search = '' OR " +
            "  LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "  LOWER(p.sku) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "  LOWER(p.barcode) LIKE LOWER(CONCAT('%', :search, '%'))) " +
            "AND (:categoryId IS NULL OR p.category.id = :categoryId) " +
            "AND (:status IS NULL OR p.status = :status)")
    Page<Product> search(@Param("search") String search,
                          @Param("categoryId") Long categoryId,
                          @Param("status") ProductStatus status,
                          Pageable pageable);
}
