package com.hacisimsek.product.config;

import com.hacisimsek.product.model.Product;
import com.hacisimsek.product.repository.ProductRepository;
import com.hacisimsek.product.service.EmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Backfills vector embeddings for products that don't have one yet.
 * Runs once at startup AFTER data.sql seeding — keeps the demo catalog searchable.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EmbeddingInitializer implements CommandLineRunner {

    private final ProductRepository productRepository;
    private final EmbeddingService embeddingService;

    @Override
    @Transactional
    public void run(String... args) {
        List<Product> missing = productRepository.findProductsMissingEmbedding();
        if (missing.isEmpty()) {
            log.info("Embedding backfill: no products missing embeddings");
            return;
        }

        log.info("Embedding backfill: processing {} products with {}", missing.size(), "nomic-embed-text");

        missing.forEach(product -> {
            try {
                String text = Stream.of(product.getName(), product.getBrand(), product.getDescription())
                        .filter(Objects::nonNull)
                        .filter(s -> !s.isBlank())
                        .collect(Collectors.joining(" "));
                float[] vec = embeddingService.embed(text);
                productRepository.updateEmbedding(product.getId(), EmbeddingService.vectorLiteral(vec));
            } catch (Exception e) {
                log.warn("Embedding backfill failed for product {}: {}",
                        product.getName(), e.getMessage());
            }
        });
    }
}