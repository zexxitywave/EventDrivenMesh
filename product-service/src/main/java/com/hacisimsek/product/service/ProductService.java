package com.hacisimsek.product.service;

import com.hacisimsek.product.dto.ProductRequest;
import com.hacisimsek.product.dto.ProductResponse;
import com.hacisimsek.product.dto.ProductStatusUpdateRequest;
import com.hacisimsek.product.model.Category;
import com.hacisimsek.product.model.Product;
import com.hacisimsek.product.model.ProductStatus;
import com.hacisimsek.product.repository.CategoryRepository;
import com.hacisimsek.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    // ── CRUD ──────────────────────────────────────────────────────────────────

    @Transactional
    public ProductResponse createProduct(UUID sellerId, ProductRequest request) {
        if (productRepository.existsBySku(request.getSku())) {
            throw new RuntimeException("SKU already exists: " + request.getSku());
        }

        Product.ProductBuilder builder = Product.builder()
                .name(request.getName())
                .description(request.getDescription())
                .sku(request.getSku())
                .price(request.getPrice())
                .originalPrice(request.getOriginalPrice())
                .sellerId(sellerId)
                .status(request.getStatus() != null ? request.getStatus() : ProductStatus.ACTIVE)
                .imageUrl(request.getImageUrl())
                .additionalImages(request.getAdditionalImages() != null ? request.getAdditionalImages() : java.util.List.of())
                .brand(request.getBrand())
                .weightGrams(request.getWeightGrams());

        if (request.getCategoryId() != null) {
            Category category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new RuntimeException("Category not found: " + request.getCategoryId()));
            builder.category(category);
        }

        Product saved = productRepository.save(builder.build());
        log.info("Product created: {} (SKU: {})", saved.getName(), saved.getSku());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public ProductResponse getProductById(UUID id) {
        return toResponse(findProduct(id));
    }

    @Transactional(readOnly = true)
    public ProductResponse getProductBySku(String sku) {
        return toResponse(productRepository.findBySku(sku)
                .orElseThrow(() -> new RuntimeException("Product not found with SKU: " + sku)));
    }

    @Transactional
    public ProductResponse updateProduct(UUID id, UUID sellerId, ProductRequest request) {
        Product product = findProduct(id);

        // Only the owner seller or admin can update — enforce in controller via role check if needed
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        if (request.getOriginalPrice() != null) product.setOriginalPrice(request.getOriginalPrice());
        if (request.getStatus() != null)        product.setStatus(request.getStatus());
        if (request.getImageUrl() != null)      product.setImageUrl(request.getImageUrl());
        if (request.getAdditionalImages() != null) product.setAdditionalImages(request.getAdditionalImages());
        if (request.getBrand() != null)         product.setBrand(request.getBrand());
        if (request.getWeightGrams() != null)   product.setWeightGrams(request.getWeightGrams());

        if (request.getCategoryId() != null) {
            Category category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new RuntimeException("Category not found: " + request.getCategoryId()));
            product.setCategory(category);
        }

        Product updated = productRepository.save(product);
        return toResponse(updated);
    }

    @Transactional
    public ProductResponse updateProductStatus(UUID id, ProductStatusUpdateRequest request) {
        Product product = findProduct(id);
        product.setStatus(request.getStatus());
        return toResponse(productRepository.save(product));
    }

    @Transactional
    public void deleteProduct(UUID id) {
        Product product = findProduct(id);
        productRepository.delete(product);
        log.info("Product deleted: {}", id);
    }

    // ── Search & Filter with Pagination ──────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<ProductResponse> searchProducts(
            String keyword,
            UUID categoryId,
            ProductStatus status,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            String brand,
            Pageable pageable) {

        return productRepository
                .search(keyword, categoryId, status, minPrice, maxPrice, brand, pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> getProductsByCategory(UUID categoryId, Pageable pageable) {
        return productRepository.findByCategoryId(categoryId, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> getProductsBySeller(UUID sellerId, Pageable pageable) {
        return productRepository.findBySellerId(sellerId, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> getAllProducts(Pageable pageable) {
        return productRepository.findAll(pageable).map(this::toResponse);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Product findProduct(UUID id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found: " + id));
    }

    public ProductResponse toResponse(Product p) {
        return ProductResponse.builder()
                .id(p.getId())
                .name(p.getName())
                .description(p.getDescription())
                .sku(p.getSku())
                .price(p.getPrice())
                .originalPrice(p.getOriginalPrice())
                .categoryId(p.getCategory() != null ? p.getCategory().getId() : null)
                .categoryName(p.getCategory() != null ? p.getCategory().getName() : null)
                .sellerId(p.getSellerId())
                .status(p.getStatus())
                .imageUrl(p.getImageUrl())
                .additionalImages(p.getAdditionalImages())
                .brand(p.getBrand())
                .weightGrams(p.getWeightGrams())
                .averageRating(p.getAverageRating())
                .ratingCount(p.getRatingCount())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }
}
