package com.risingcrescent.repo;

import com.risingcrescent.domain.entity.Product;
import com.risingcrescent.domain.enums.ProductKind;
import com.risingcrescent.domain.enums.ProductStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {
    Optional<Product> findBySku(String sku);
    List<Product> findByStatus(ProductStatus status);
    List<Product> findByStatusAndKind(ProductStatus status, ProductKind kind);
    List<Product> findByStatusAndCategory_Id(ProductStatus status, Long categoryId);
    List<Product> findByNameContainingIgnoreCase(String q);
    List<Product> findByCategory_Id(Long categoryId);
}
