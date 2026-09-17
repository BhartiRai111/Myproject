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

    /** Stock-keeping identifier — always stored trimmed and uppercased (see ProductService#normalizeSku). */
    @Column(length = 50, unique = true)
    private String sku;

    /** Trimmed only (never uppercased/parsed as a number) so leading zeroes and Code128 case survive exactly. */
    @Column(length = 50, unique = true)
    private String barcode;

    /** Auto-detected from the barcode's shape by BarcodeUtil.detectType — never user-selected. Null when barcode is null. */
    @Enumerated(EnumType.STRING)
    @Column(name = "barcode_type", length = 20)
    private BarcodeType barcodeType;

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

    /** GST tax treatment — kept separate from {@link #tax} (spec sections 15/16). Defaults to TAXABLE. */
    @Enumerated(EnumType.STRING)
    @Column(name = "tax_treatment", length = 20)
    private TaxTreatment taxTreatment;

    @Column(name = "min_stock_level")
    private Integer minStockLevel;

    @Column(name = "reorder_level")
    private Integer reorderLevel;

    @Column(name = "reorder_quantity")
    private Integer reorderQuantity;

    /**
     * Overstock threshold — moved here from {@code Inventory} for Multi-Store (spec
     * sections 82-83): it is Item Master configuration ("how much of this item is too
     * much"), not a per-store quantity, so it must not be duplicated across a product's
     * now-multiple per-store Inventory rows.
     */
    @Column(name = "max_stock_level")
    private Integer maxStockLevel;

    @Column(name = "mrp", precision = 12, scale = 2)
    private BigDecimal mrp;

    @Column(name = "wholesale_price", precision = 12, scale = 2)
    private BigDecimal wholesalePrice;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ProductStatus status;

    @Column(length = 500)
    private String description;

    /** Optional internal/business reference code — distinct from {@link #sku}, which is the stock-keeping identifier. */
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

    /** Free-text descriptive metadata from the original Item Master tab — NOT read by GST calculation. See {@link #taxTreatment} for the enum that actually drives tax. */
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
        if (this.taxTreatment == null) {
            this.taxTreatment = TaxTreatment.TAXABLE;
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
