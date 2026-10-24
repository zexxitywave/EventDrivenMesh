package com.hacisimsek.product.service;

import com.hacisimsek.product.dto.CategoryRequest;
import com.hacisimsek.product.model.Category;
import com.hacisimsek.product.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CategoryService {

    private final CategoryRepository categoryRepository;

    @Transactional
    public Category createCategory(CategoryRequest request) {
        if (categoryRepository.existsByName(request.getName())) {
            throw new RuntimeException("Category already exists: " + request.getName());
        }

        Category.CategoryBuilder builder = Category.builder()
                .name(request.getName())
                .description(request.getDescription())
                .slug(request.getSlug() != null
                        ? request.getSlug()
                        : toSlug(request.getName()));

        if (request.getParentId() != null) {
            Category parent = categoryRepository.findById(request.getParentId())
                    .orElseThrow(() -> new RuntimeException("Parent category not found: " + request.getParentId()));
            builder.parent(parent);
        }

        Category saved = categoryRepository.save(builder.build());
        log.info("Category created: {}", saved.getName());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Category> getAllCategories() {
        return categoryRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Category getCategoryById(UUID id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Category not found: " + id));
    }

    @Transactional
    public Category updateCategory(UUID id, CategoryRequest request) {
        Category category = getCategoryById(id);
        category.setName(request.getName());
        category.setDescription(request.getDescription());
        if (request.getSlug() != null) category.setSlug(request.getSlug());

        if (request.getParentId() != null) {
            Category parent = categoryRepository.findById(request.getParentId())
                    .orElseThrow(() -> new RuntimeException("Parent category not found: " + request.getParentId()));
            category.setParent(parent);
        }

        return categoryRepository.save(category);
    }

    @Transactional
    public void deleteCategory(UUID id) {
        Category category = getCategoryById(id);
        categoryRepository.delete(category);
        log.info("Category deleted: {}", id);
    }

    private String toSlug(String name) {
        return name.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    }

//    Category name                  Slug
//------------------------------------------------
//    Electronics                    electronics
//    Mobile Phones                  mobile-phones
//    Men's Fashion                  mens-fashion
//    Home & Kitchen                 home-kitchen
//    Sports & Fitness               sports-fitness
}
