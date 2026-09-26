package com.shiningcrescent.web;

import com.shiningcrescent.repo.CategoryRepository;
import com.shiningcrescent.service.CatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/catalog")
@RequiredArgsConstructor
public class CatalogController {
    private final CatalogService catalog;
    private final CategoryRepository categories;

    @GetMapping("/categories")
    public Object categories() {
        return categories.findAll();
    }

    @GetMapping("/products")
    public List<Map<String, Object>> products(
            @RequestParam(required = false) String kind,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String q) {
        return catalog.storefront(kind, categoryId, q);
    }

    @GetMapping("/products/{id}")
    public Map<String, Object> product(@PathVariable Long id) {
        return catalog.detail(id);
    }
}
