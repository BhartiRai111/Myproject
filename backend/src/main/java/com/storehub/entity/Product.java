package com.storehub.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 150)
    private String name;

    @Column(length = 50, unique = true)
    private String sku;

    @Column(length = 50, unique = true)
    private String barcode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(length = 100)
    private String brand;

    @Column(length = 20)
    private String unit;

    @Column(name = "purchase_price", precision = 12, scale = 2)
    private BigDecimal purchasePrice;

    @Column(name = "selling_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal sellingPrice;

    @Column(precision = 5, scale = 2)
    private BigDecimal tax;

    @Column(name = "min_stock_level")
    private Integer minStockLevel;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ProductStatus status;

    @Column(length = 500)
    private String description;

    @Column(name = "manual_code", length = 50)
    private String manualCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_group_id")
    private ItemGroup itemGroup;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hsn_id")
    private Hsn hsn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_unit_id")
    private Unit purchaseUnit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_unit_id")
    private Unit saleUnit;

    @Column(name = "tolerance_percent", precision = 5, scale = 2)
    private BigDecimal tolerancePercent;

    @Column(name = "item_type", length = 50)
    private String itemType;

    @Column(name = "tax_nature", length = 50)
    private String taxNature;

    @Column(name = "tax_based_on", length = 50)
    private String taxBasedOn;

    @Column(name = "party_name", length = 150)
    private String partyName;

    @Column(name = "party_product_name", length = 150)
    private String partyProductName;

    @Column(name = "free_value", precision = 12, scale = 2)
    private BigDecimal freeValue;

    @Column(name = "applicable_property", length = 100)
    private String applicableProperty;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.unit == null || this.unit.isBlank()) {
            this.unit = "pcs";
        }
        if (this.purchasePrice == null) {
            this.purchasePrice = BigDecimal.ZERO;
        }
        if (this.sellingPrice == null) {
            this.sellingPrice = BigDecimal.ZERO;
        }
        if (this.tax == null) {
            this.tax = BigDecimal.ZERO;
        }
        if (this.minStockLevel == null) {
            this.minStockLevel = 0;
        }
        if (this.status == null) {
            this.status = ProductStatus.ACTIVE;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
