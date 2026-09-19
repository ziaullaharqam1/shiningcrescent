package com.risingcrescent.service;

import com.risingcrescent.domain.entity.InventoryLot;
import com.risingcrescent.domain.entity.Product;
import com.risingcrescent.domain.enums.LotStatus;
import com.risingcrescent.domain.enums.ProductKind;
import com.risingcrescent.domain.enums.ProductStatus;
import com.risingcrescent.repo.InventoryLotRepository;
import com.risingcrescent.repo.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CatalogService {
    private final ProductRepository products;
    private final InventoryLotRepository lots;

    public List<Map<String, Object>> storefront(String kind, Long categoryId, String q) {
        List<Product> list = products.findByStatus(ProductStatus.PUBLISHED);
        if (kind != null && !kind.isBlank()) {
            ProductKind k = ProductKind.valueOf(kind);
            list = list.stream().filter(p -> p.getKind() == k).toList();
        }
        if (categoryId != null) {
            list = list.stream().filter(p -> p.getCategory() != null && p.getCategory().getId().equals(categoryId)).toList();
        }
        if (q != null && !q.isBlank()) {
            String n = q.toLowerCase();
            list = list.stream().filter(p -> p.getName().toLowerCase().contains(n)
                    || (p.getOrigin() != null && p.getOrigin().toLowerCase().contains(n))
                    || p.getSku().toLowerCase().contains(n)).toList();
        }
        return list.stream().map(this::card).toList();
    }

    public Map<String, Object> detail(Long id) {
        Product p = products.findById(id).orElseThrow(() -> new IllegalArgumentException("Product not found"));
        Map<String, Object> m = card(p);
        m.put("description", p.getDescription());
        m.put("season", p.getSeason());
        m.put("variety", p.getVariety());
        m.put("shelfLifeDays", p.getShelfLifeDays());
        m.put("moistureMaxPct", p.getMoistureMaxPct());
        m.put("storageHint", p.getStorageHint());
        m.put("minWholesaleQty", p.getMinWholesaleQty());
        m.put("lots", lots.findByProduct_Id(id).stream().map(this::lotView).toList());
        return m;
    }

    public Map<String, Object> card(Product p) {
        BigDecimal available = lots.findByProduct_IdAndStatusOrderByExpiryOnAsc(p.getId(), LotStatus.AVAILABLE)
                .stream().map(InventoryLot::getAvailableQty).reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId());
        m.put("sku", p.getSku());
        m.put("name", p.getName());
        m.put("kind", p.getKind());
        m.put("uom", p.getUom());
        m.put("origin", p.getOrigin());
        m.put("grade", p.getGrade());
        m.put("category", p.getCategory() != null ? p.getCategory().getName() : "");
        m.put("categoryId", p.getCategory() != null ? p.getCategory().getId() : null);
        m.put("retailPrice", p.getRetailPrice());
        m.put("wholesalePrice", p.getWholesalePrice());
        m.put("imageHint", p.getImageHint());
        m.put("imageUrl", p.getImageUrl());
        m.put("status", p.getStatus());
        m.put("availableQty", available);
        m.put("perishable", p.isPerishable());
        return m;
    }

    private Map<String, Object> lotView(InventoryLot lot) {
        return Map.of(
                "lotCode", lot.getLotCode(),
                "status", lot.getStatus(),
                "availableQty", lot.getAvailableQty(),
                "expiryOn", lot.getExpiryOn() == null ? "" : lot.getExpiryOn().toString(),
                "grade", lot.getGrade() == null ? "" : lot.getGrade()
        );
    }
}
