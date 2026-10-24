package com.hacisimsek.product.repository;

import com.hacisimsek.product.model.Product;
import com.hacisimsek.product.model.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    // ── Single lookups ────────────────────────────────────────────────────────

    Optional<Product> findBySku(String sku);

    boolean existsBySku(String sku);

    // ── Vector search ────────────────────────────────────────────────────────
    // pgvector: returns products ordered by cosine distance to a query vector.
    // The cast(:queryVec AS vector) converts the literal '[0.1,...]' into a Postgres vector.
    @Query(value = """
        SELECT id FROM products
        WHERE embedding IS NOT NULL
        ORDER BY embedding <=> cast(:queryVec AS vector)
        LIMIT :limit
        """, nativeQuery = true)
    List<UUID> findSimilar(@Param("queryVec") String queryVec, @Param("limit") int limit);

    @Query(value = "SELECT * FROM products WHERE embedding IS NULL", nativeQuery = true)
    List<Product> findProductsMissingEmbedding();

    // Hibernate cannot bind PGobject for the vector column (bytea mismatch), so we
    // write embeddings with a native UPDATE and an explicit cast.
    @Modifying
    @Query(value = "UPDATE products SET embedding = cast(:vec AS vector) WHERE id = :id", nativeQuery = true)
    void updateEmbedding(@Param("id") UUID id, @Param("vec") String vec);

    // ── List queries with JOIN FETCH to eliminate N+1 on category ────────────
    // Without JOIN FETCH, fetching 100 products fires 101 queries:
    //   1 SELECT * FROM products
    //   100 SELECT * FROM categories WHERE id = ?  (one per product)
    // With JOIN FETCH, it's a single query with a LEFT JOIN.

    @Query("""
        SELECT p FROM Product p
        LEFT JOIN FETCH p.category
        WHERE p.status = :status
    """)
//    The main reason we used JOIN FETCH in your ProductRepository is to prevent
//    the N+1 query problem when your code needs Product and its Category.
    Page<Product> findByStatus(@Param("status") ProductStatus status, Pageable pageable);

    @Query("""
        SELECT p FROM Product p
        LEFT JOIN FETCH p.category
        WHERE p.category.id = :categoryId
    """)
    Page<Product> findByCategoryId(@Param("categoryId") UUID categoryId, Pageable pageable);

    @Query("""
        SELECT p FROM Product p
        LEFT JOIN FETCH p.category
        WHERE p.sellerId = :sellerId
    """)
    Page<Product> findBySellerId(@Param("sellerId") UUID sellerId, Pageable pageable);

    // ── Full-text search with JOIN FETCH ──────────────────────────────────────

    @Query("""
        SELECT p FROM Product p
        LEFT JOIN FETCH p.category
        WHERE (
            CAST(:keyword AS string) IS NULL OR
            LOWER(COALESCE(p.name, '')) LIKE CONCAT('%', LOWER(CAST(:keyword AS string)), '%') OR
            LOWER(COALESCE(p.description, '')) LIKE CONCAT('%', LOWER(CAST(:keyword AS string)), '%') OR
            LOWER(COALESCE(p.brand, '')) LIKE CONCAT('%', LOWER(CAST(:keyword AS string)), '%')
        )
        AND (:categoryId IS NULL OR p.category.id = :categoryId)
        AND (:status IS NULL OR p.status = :status)
        AND (:minPrice IS NULL OR p.price >= :minPrice)
        AND (:maxPrice IS NULL OR p.price <= :maxPrice)
        AND (CAST(:brand AS string) IS NULL OR p.brand = CAST(:brand AS string))
    """)
    Page<Product> search(
            @Param("keyword")    String keyword,
            @Param("categoryId") UUID categoryId,
            @Param("status")     ProductStatus status,
            @Param("minPrice")   BigDecimal minPrice,
            @Param("maxPrice")   BigDecimal maxPrice,
            @Param("brand")      String brand,
            Pageable pageable
    );
}
