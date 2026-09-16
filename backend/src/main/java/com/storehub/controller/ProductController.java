package com.storehub.controller;

import com.storehub.dto.GenerateBarcodeResponse;
import com.storehub.dto.GenerateSkuResponse;
import com.storehub.dto.ImportResultResponse;
import com.storehub.dto.PagedResponse;
import com.storehub.dto.ProductCreateRequest;
import com.storehub.dto.ProductResponse;
import com.storehub.dto.ProductUpdateRequest;
import com.storehub.entity.ProductStatus;
import com.storehub.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public ResponseEntity<PagedResponse<ProductResponse>> getProducts(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) ProductStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        return ResponseEntity.ok(productService.searchProducts(search, categoryId, status, page, size, sortBy, sortDir));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getProductById(@PathVariable Long id) {
        return ResponseEntity.ok(productService.getProductById(id));
    }

    /**
     * Authoritative barcode lookup for POS/Sales/Purchase scanning (Barcode
     * Management spec section 21). Open to any authenticated user, same as
     * {@link #getProductById}, since any staff member operating a scanner
     * needs it. 404 when no item has this barcode, 400 when the item exists
     * but is inactive — see {@link ProductService#findByBarcode}.
     */
    @GetMapping("/barcode/{barcode}")
    public ResponseEntity<ProductResponse> getProductByBarcode(@PathVariable String barcode) {
        return ResponseEntity.ok(productService.findByBarcode(barcode));
    }

    @PostMapping("/generate-sku")
    @PreAuthorize("hasAuthority('PERM_ITEM_CREATE')")
    public ResponseEntity<GenerateSkuResponse> generateSku() {
        return ResponseEntity.ok(new GenerateSkuResponse(productService.generateSku()));
    }

    @PostMapping("/generate-barcode")
    @PreAuthorize("hasAuthority('PERM_ITEM_CREATE')")
    public ResponseEntity<GenerateBarcodeResponse> generateBarcode() {
        return ResponseEntity.ok(new GenerateBarcodeResponse(productService.generateBarcode()));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_ITEM_CREATE')")
    public ResponseEntity<ProductResponse> createProduct(@Valid @RequestBody ProductCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.createProduct(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_ITEM_EDIT')")
    public ResponseEntity<ProductResponse> updateProduct(@PathVariable Long id,
                                                           @Valid @RequestBody ProductUpdateRequest request) {
        return ResponseEntity.ok(productService.updateProduct(id, request));
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('PERM_ITEM_EDIT')")
    public ResponseEntity<ProductResponse> activateProduct(@PathVariable Long id) {
        return ResponseEntity.ok(productService.setStatus(id, ProductStatus.ACTIVE));
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasAuthority('PERM_ITEM_EDIT')")
    public ResponseEntity<ProductResponse> deactivateProduct(@PathVariable Long id) {
        return ResponseEntity.ok(productService.setStatus(id, ProductStatus.INACTIVE));
    }

    @GetMapping("/export")
    @PreAuthorize("hasAuthority('PERM_ITEM_EDIT')")
    public ResponseEntity<byte[]> exportProducts(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) ProductStatus status) {
        String csv = productService.exportCsv(search, categoryId, status);
        String filename = "products-" + LocalDate.now() + ".csv";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }

    @PostMapping("/import")
    @PreAuthorize("hasAuthority('PERM_ITEM_CREATE')")
    public ResponseEntity<ImportResultResponse> importProducts(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(productService.importCsv(file));
    }
}
