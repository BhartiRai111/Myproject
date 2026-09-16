package com.storehub.service;

import com.storehub.dto.ImportResultResponse;
import com.storehub.dto.PagedResponse;
import com.storehub.dto.ProductCreateRequest;
import com.storehub.dto.ProductResponse;
import com.storehub.dto.ProductUpdateRequest;
import com.storehub.entity.AuditAction;
import com.storehub.entity.Category;
import com.storehub.entity.Hsn;
import com.storehub.entity.ItemGroup;
import com.storehub.entity.Product;
import com.storehub.entity.ProductStatus;
import com.storehub.entity.ReferenceType;
import com.storehub.entity.StockMovementType;
import com.storehub.entity.Unit;
import com.storehub.exception.BadRequestException;
import com.storehub.exception.ProductNotFoundException;
import com.storehub.repository.CategoryRepository;
import com.storehub.repository.ProductRepository;
import com.storehub.repository.StockHistoryRepository;
import com.storehub.util.CsvUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ProductService {

    private static final Set<String> SORTABLE_FIELDS = Set.of(
            "name", "sku", "purchasePrice", "sellingPrice", "createdAt");

    private static final List<String> EXPORT_HEADER = List.of(
            "name", "sku", "barcode", "category", "brand", "unit", "purchasePrice", "sellingPrice", "tax",
            "minStockLevel", "reorderLevel", "reorderQuantity", "maxStockLevel", "mrp", "wholesalePrice",
            "currentStock", "status", "description");

    private static final List<String> IMPORT_REQUIRED_COLUMNS = List.of("name", "sku", "category", "purchasePrice", "sellingPrice");

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final CategoryService categoryService;
    private final InventoryService inventoryService;
    private final ItemGroupService itemGroupService;
    private final HsnService hsnService;
    private final UnitService unitService;
    private final StockHistoryRepository stockHistoryRepository;
    private final AuditService auditService;
    private final SkuGeneratorService skuGeneratorService;

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
        return ProductResponse.fromEntity(product, inventoryService.getCurrentStock(id), inventoryService.getMaxStockLevel(id),
                stockHistoryRepository.existsByProductId(id));
    }

    /**
     * Canonical SKU form used consistently across create/update/import/search
     * (SKU Management spec section 6) — trims whitespace and uppercases so
     * {@code "  abc-001 "} and {@code "ABC-001"} are recognized as the same
     * SKU everywhere, not just wherever a case-insensitive DB query happens
     * to be used.
     */
    private String normalizeSku(String sku) {
        return sku == null ? null : sku.trim().toUpperCase();
    }

    @Transactional
    public ProductResponse createProduct(ProductCreateRequest request) {
        String sku = normalizeSku(request.getSku());
        if (productRepository.existsByNameIgnoreCase(request.getName())) {
            throw new BadRequestException("A product named '" + request.getName() + "' already exists");
        }
        if (productRepository.existsBySkuIgnoreCase(sku)) {
            throw new BadRequestException("SKU '" + sku + "' already exists. Please use a different SKU.");
        }
        if (request.getBarcode() != null && !request.getBarcode().isBlank()
                && productRepository.existsByBarcodeIgnoreCase(request.getBarcode())) {
            throw new BadRequestException("A product with barcode '" + request.getBarcode() + "' already exists");
        }

        Category category = categoryService.findCategoryOrThrow(request.getCategoryId());
        ItemGroup itemGroup = request.getItemGroupId() != null ? itemGroupService.findOrThrow(request.getItemGroupId()) : null;
        Hsn hsn = request.getHsnId() != null ? hsnService.findOrThrow(request.getHsnId()) : null;
        Unit purchaseUnit = request.getPurchaseUnitId() != null ? unitService.findOrThrow(request.getPurchaseUnitId()) : null;
        Unit saleUnit = request.getSaleUnitId() != null ? unitService.findOrThrow(request.getSaleUnitId()) : null;

        Product product = Product.builder()
                .name(request.getName())
                .sku(sku)
                .barcode(blankToNull(request.getBarcode()))
                .category(category)
                .brand(request.getBrand())
                .unit(request.getUnit())
                .purchasePrice(request.getPurchasePrice())
                .sellingPrice(request.getSellingPrice())
                .tax(request.getTax())
                .minStockLevel(request.getMinStockLevel())
                .reorderLevel(request.getReorderLevel())
                .reorderQuantity(request.getReorderQuantity())
                .mrp(request.getMrp())
                .wholesalePrice(request.getWholesalePrice())
                .description(request.getDescription())
                .manualCode(request.getManualCode())
                .itemGroup(itemGroup)
                .hsn(hsn)
                .purchaseUnit(purchaseUnit)
                .saleUnit(saleUnit)
                .tolerancePercent(request.getTolerancePercent())
                .itemType(request.getItemType())
                .taxNature(request.getTaxNature())
                .taxBasedOn(request.getTaxBasedOn())
                .partyName(request.getPartyName())
                .partyProductName(request.getPartyProductName())
                .freeValue(request.getFreeValue())
                .applicableProperty(request.getApplicableProperty())
                .build();

        Product saved = productRepository.save(product);
        inventoryService.createInventoryForProduct(saved);
        if (request.getMaxStockLevel() != null) {
            inventoryService.updateMaxStockLevel(saved.getId(), request.getMaxStockLevel());
        }

        auditService.log(AuditAction.CREATE, "ITEM_MASTER", "Product", saved.getId(), saved.getSku(),
                null, null, "Item '" + saved.getName() + "' (SKU " + saved.getSku() + ") created");

        return ProductResponse.fromEntity(saved, 0, request.getMaxStockLevel());
    }

    @Transactional
    public ProductResponse updateProduct(Long id, ProductUpdateRequest request) {
        Product product = findProductOrThrow(id);
        String oldSku = product.getSku();
        String newSku = normalizeSku(request.getSku());
        boolean skuChanging = oldSku == null || !oldSku.equalsIgnoreCase(newSku);

        if (!product.getName().equalsIgnoreCase(request.getName())
                && productRepository.existsByNameIgnoreCase(request.getName())) {
            throw new BadRequestException("A product named '" + request.getName() + "' already exists");
        }
        if (skuChanging && productRepository.existsBySkuIgnoreCaseAndIdNot(newSku, id)) {
            throw new BadRequestException("SKU '" + newSku + "' already exists. Please use a different SKU.");
        }
        if (skuChanging && stockHistoryRepository.existsByProductId(id)) {
            throw new BadRequestException("SKU cannot be changed because this item already has recorded stock, sales, or "
                    + "purchase history. Deactivate this item and create a new one instead if a new SKU is required.");
        }
        String newBarcode = blankToNull(request.getBarcode());
        if (newBarcode != null && !newBarcode.equalsIgnoreCase(product.getBarcode())
                && productRepository.existsByBarcodeIgnoreCaseAndIdNot(newBarcode, id)) {
            throw new BadRequestException("A product with barcode '" + newBarcode + "' already exists");
        }

        Category category = categoryService.findCategoryOrThrow(request.getCategoryId());
        ItemGroup itemGroup = request.getItemGroupId() != null ? itemGroupService.findOrThrow(request.getItemGroupId()) : null;
        Hsn hsn = request.getHsnId() != null ? hsnService.findOrThrow(request.getHsnId()) : null;
        Unit purchaseUnit = request.getPurchaseUnitId() != null ? unitService.findOrThrow(request.getPurchaseUnitId()) : null;
        Unit saleUnit = request.getSaleUnitId() != null ? unitService.findOrThrow(request.getSaleUnitId()) : null;

        product.setName(request.getName());
        product.setSku(newSku);
        product.setBarcode(newBarcode);
        product.setCategory(category);
        product.setBrand(request.getBrand());
        product.setUnit(request.getUnit());
        product.setPurchasePrice(request.getPurchasePrice());
        product.setSellingPrice(request.getSellingPrice());
        product.setTax(request.getTax());
        product.setMinStockLevel(request.getMinStockLevel());
        product.setReorderLevel(request.getReorderLevel());
        product.setReorderQuantity(request.getReorderQuantity());
        product.setMrp(request.getMrp());
        product.setWholesalePrice(request.getWholesalePrice());
        product.setDescription(request.getDescription());
        product.setManualCode(request.getManualCode());
        product.setItemGroup(itemGroup);
        product.setHsn(hsn);
        product.setPurchaseUnit(purchaseUnit);
        product.setSaleUnit(saleUnit);
        product.setTolerancePercent(request.getTolerancePercent());
        product.setItemType(request.getItemType());
        product.setTaxNature(request.getTaxNature());
        product.setTaxBasedOn(request.getTaxBasedOn());
        product.setPartyName(request.getPartyName());
        product.setPartyProductName(request.getPartyProductName());
        product.setFreeValue(request.getFreeValue());
        product.setApplicableProperty(request.getApplicableProperty());

        Product saved = productRepository.save(product);
        inventoryService.updateMaxStockLevel(id, request.getMaxStockLevel());

        if (skuChanging) {
            auditService.log(AuditAction.UPDATE, "ITEM_MASTER", "Product", saved.getId(), saved.getSku(),
                    oldSku, newSku, "SKU changed for item '" + saved.getName() + "'");
        } else {
            auditService.log(AuditAction.UPDATE, "ITEM_MASTER", "Product", saved.getId(), saved.getSku(),
                    null, null, "Item '" + saved.getName() + "' (SKU " + saved.getSku() + ") updated");
        }

        return ProductResponse.fromEntity(saved, inventoryService.getCurrentStock(id), request.getMaxStockLevel(),
                stockHistoryRepository.existsByProductId(id));
    }

    @Transactional
    public ProductResponse setStatus(Long id, ProductStatus status) {
        Product product = findProductOrThrow(id);
        product.setStatus(status);
        Product saved = productRepository.save(product);
        auditService.log(AuditAction.UPDATE, "ITEM_MASTER", "Product", saved.getId(), saved.getSku(), null, null,
                "Item '" + saved.getName() + "' (SKU " + saved.getSku() + ") " + status.name().toLowerCase());
        return ProductResponse.fromEntity(saved, inventoryService.getCurrentStock(id));
    }

    /**
     * Auto-generates the next SKU (SKU Management spec section 8) — a thin
     * passthrough so the controller doesn't depend on {@link SkuGeneratorService}
     * directly, matching how every other cross-cutting concern in this class
     * (inventory, categories, HSN, units) is accessed only through this service.
     */
    public String generateSku() {
        return skuGeneratorService.generateNext();
    }

    /**
     * One-time startup safety net (SKU Management spec sections 7/19): assigns
     * an auto-generated SKU to any pre-existing product that has none, so the
     * now-mandatory-on-save SKU field never leaves a legacy record permanently
     * un-editable. Idempotent — a no-op once every product has a SKU.
     */
    @Transactional
    public void backfillMissingSkus() {
        for (Product product : productRepository.findAll()) {
            if (product.getSku() == null || product.getSku().isBlank()) {
                String sku = skuGeneratorService.generateNext();
                product.setSku(sku);
                productRepository.save(product);
                auditService.log(AuditAction.UPDATE, "ITEM_MASTER", "Product", product.getId(), sku,
                        null, sku, "SKU backfilled for legacy item '" + product.getName() + "' which had none");
            }
        }
    }

    public Product findProductOrThrow(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
    }

    // ---- CSV export/import (Phase 6 spec section 22) ----

    /** Exports the same search/category/status-filtered product set the Products list page shows. */
    public String exportCsv(String search, Long categoryId, ProductStatus status) {
        List<Product> products = productRepository.search(search, categoryId, status, Sort.by("name").ascending());
        List<Long> productIds = products.stream().map(Product::getId).toList();
        Map<Long, Integer> stockByProductId = inventoryService.getCurrentStockBulk(productIds);
        Map<Long, Integer> maxStockByProductId = inventoryService.getMaxStockLevelBulk(productIds);

        StringBuilder csv = new StringBuilder();
        csv.append(CsvUtil.row(EXPORT_HEADER.toArray()));
        for (Product p : products) {
            csv.append(CsvUtil.row(
                    p.getName(), p.getSku(), p.getBarcode(), p.getCategory() != null ? p.getCategory().getName() : "",
                    p.getBrand(), p.getUnit(), p.getPurchasePrice(), p.getSellingPrice(), p.getTax(),
                    p.getMinStockLevel(), p.getReorderLevel(), p.getReorderQuantity(), maxStockByProductId.get(p.getId()),
                    p.getMrp(), p.getWholesalePrice(), stockByProductId.getOrDefault(p.getId(), 0),
                    p.getStatus(), p.getDescription()));
        }
        return csv.toString();
    }

    /**
     * Imports products from a CSV file: creates new SKUs, updates existing ones by SKU match.
     * Opening stock (for NEW products only) is applied through {@link InventoryService#applyMovement}
     * — never a direct database write — per the Phase 6 spec's explicit requirement. Re-importing an
     * existing SKU never touches its current stock, avoiding accidental double-counting.
     */
    @Transactional
    public ImportResultResponse importCsv(MultipartFile file) {
        List<String> errors = new ArrayList<>();
        int created = 0;
        int updated = 0;
        int skipped = 0;
        int totalRows = 0;

        List<String> lines;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            lines = reader.lines().filter(l -> !l.isBlank()).toList();
        } catch (IOException e) {
            throw new BadRequestException("Failed to read the uploaded file: " + e.getMessage());
        }

        if (lines.isEmpty()) {
            throw new BadRequestException("The uploaded CSV file is empty");
        }

        List<String> header = CsvUtil.parseLine(lines.get(0));
        Map<String, Integer> columnIndex = new HashMap<>();
        for (int i = 0; i < header.size(); i++) {
            columnIndex.put(header.get(i).trim().toLowerCase(), i);
        }
        for (String required : IMPORT_REQUIRED_COLUMNS) {
            if (!columnIndex.containsKey(required.toLowerCase())) {
                throw new BadRequestException("CSV is missing required column '" + required + "'");
            }
        }

        for (int rowNum = 1; rowNum < lines.size(); rowNum++) {
            totalRows++;
            List<String> fields = CsvUtil.parseLine(lines.get(rowNum));
            String rowLabel = "Row " + (rowNum + 1);
            try {
                String name = field(fields, columnIndex, "name");
                String sku = field(fields, columnIndex, "sku");
                String categoryName = field(fields, columnIndex, "category");
                String purchasePriceStr = field(fields, columnIndex, "purchasePrice");
                String sellingPriceStr = field(fields, columnIndex, "sellingPrice");

                if (isBlank(name) || isBlank(sku) || isBlank(categoryName) || isBlank(purchasePriceStr) || isBlank(sellingPriceStr)) {
                    errors.add(rowLabel + ": missing one of the required fields (name, sku, category, purchasePrice, sellingPrice)");
                    skipped++;
                    continue;
                }

                Category category = categoryRepository.findByNameIgnoreCase(categoryName.trim()).orElse(null);
                if (category == null) {
                    errors.add(rowLabel + ": category '" + categoryName + "' not found");
                    skipped++;
                    continue;
                }

                BigDecimal purchasePrice = parseDecimal(purchasePriceStr, rowLabel, "purchasePrice", errors);
                BigDecimal sellingPrice = parseDecimal(sellingPriceStr, rowLabel, "sellingPrice", errors);
                if (purchasePrice == null || sellingPrice == null) {
                    skipped++;
                    continue;
                }

                BigDecimal tax = parseOptionalDecimal(field(fields, columnIndex, "tax"));
                Integer minStockLevel = parseOptionalInt(field(fields, columnIndex, "minStockLevel"));
                Integer reorderLevel = parseOptionalInt(field(fields, columnIndex, "reorderLevel"));
                Integer reorderQuantity = parseOptionalInt(field(fields, columnIndex, "reorderQuantity"));
                Integer maxStockLevel = parseOptionalInt(field(fields, columnIndex, "maxStockLevel"));
                BigDecimal mrp = parseOptionalDecimal(field(fields, columnIndex, "mrp"));
                BigDecimal wholesalePrice = parseOptionalDecimal(field(fields, columnIndex, "wholesalePrice"));
                Integer openingStock = parseOptionalInt(field(fields, columnIndex, "openingStock"));
                String barcode = blankToNull(field(fields, columnIndex, "barcode"));
                String brand = field(fields, columnIndex, "brand");
                String unit = field(fields, columnIndex, "unit");
                String description = field(fields, columnIndex, "description");

                String normalizedSku = normalizeSku(sku);
                Product existing = productRepository.findBySkuIgnoreCase(normalizedSku).orElse(null);
                if (existing != null) {
                    if (!existing.getName().equalsIgnoreCase(name.trim())
                            && productRepository.existsByNameIgnoreCase(name.trim())) {
                        errors.add(rowLabel + ": another product is already named '" + name + "'");
                        skipped++;
                        continue;
                    }
                    existing.setName(name.trim());
                    existing.setCategory(category);
                    existing.setBrand(blankToNull(brand));
                    existing.setUnit(isBlank(unit) ? existing.getUnit() : unit.trim());
                    existing.setPurchasePrice(purchasePrice);
                    existing.setSellingPrice(sellingPrice);
                    if (tax != null) existing.setTax(tax);
                    if (minStockLevel != null) existing.setMinStockLevel(minStockLevel);
                    existing.setReorderLevel(reorderLevel);
                    existing.setReorderQuantity(reorderQuantity);
                    existing.setMrp(mrp);
                    existing.setWholesalePrice(wholesalePrice);
                    existing.setDescription(blankToNull(description));
                    if (barcode != null && !barcode.equalsIgnoreCase(existing.getBarcode())
                            && productRepository.existsByBarcodeIgnoreCase(barcode)) {
                        errors.add(rowLabel + ": barcode '" + barcode + "' is already used by another product");
                        skipped++;
                        continue;
                    }
                    existing.setBarcode(barcode);
                    Product saved = productRepository.save(existing);
                    if (maxStockLevel != null) {
                        inventoryService.updateMaxStockLevel(saved.getId(), maxStockLevel);
                    }
                    updated++;
                } else {
                    if (productRepository.existsByNameIgnoreCase(name.trim())) {
                        errors.add(rowLabel + ": a product named '" + name + "' already exists");
                        skipped++;
                        continue;
                    }
                    if (barcode != null && productRepository.existsByBarcodeIgnoreCase(barcode)) {
                        errors.add(rowLabel + ": barcode '" + barcode + "' is already used by another product");
                        skipped++;
                        continue;
                    }
                    Product product = Product.builder()
                            .name(name.trim())
                            .sku(normalizedSku)
                            .barcode(barcode)
                            .category(category)
                            .brand(blankToNull(brand))
                            .unit(isBlank(unit) ? "pcs" : unit.trim())
                            .purchasePrice(purchasePrice)
                            .sellingPrice(sellingPrice)
                            .tax(tax != null ? tax : BigDecimal.ZERO)
                            .minStockLevel(minStockLevel != null ? minStockLevel : 0)
                            .reorderLevel(reorderLevel)
                            .reorderQuantity(reorderQuantity)
                            .mrp(mrp)
                            .wholesalePrice(wholesalePrice)
                            .description(blankToNull(description))
                            .build();
                    Product saved = productRepository.save(product);
                    inventoryService.createInventoryForProduct(saved);
                    if (maxStockLevel != null) {
                        inventoryService.updateMaxStockLevel(saved.getId(), maxStockLevel);
                    }
                    if (openingStock != null && openingStock > 0) {
                        inventoryService.applyMovement(saved.getId(), openingStock, StockMovementType.STOCK_IN,
                                ReferenceType.MANUAL, null, "Opening stock via CSV import");
                    }
                    created++;
                }
            } catch (Exception e) {
                errors.add(rowLabel + ": " + e.getMessage());
                skipped++;
            }
        }

        return ImportResultResponse.builder()
                .totalRows(totalRows)
                .created(created)
                .updated(updated)
                .skipped(skipped)
                .errors(errors)
                .build();
    }

    private String field(List<String> fields, Map<String, Integer> columnIndex, String column) {
        Integer idx = columnIndex.get(column.toLowerCase());
        if (idx == null || idx >= fields.size()) return null;
        return fields.get(idx);
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private BigDecimal parseDecimal(String value, String rowLabel, String fieldName, List<String> errors) {
        try {
            BigDecimal result = new BigDecimal(value.trim());
            if (result.signum() < 0) {
                errors.add(rowLabel + ": " + fieldName + " cannot be negative");
                return null;
            }
            return result;
        } catch (NumberFormatException e) {
            errors.add(rowLabel + ": '" + value + "' is not a valid number for " + fieldName);
            return null;
        }
    }

    private BigDecimal parseOptionalDecimal(String value) {
        if (isBlank(value)) return null;
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer parseOptionalInt(String value) {
        if (isBlank(value)) return null;
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
