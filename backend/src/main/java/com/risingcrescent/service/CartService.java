package com.risingcrescent.service;

import com.risingcrescent.audit.AuditService;
import com.risingcrescent.domain.entity.Cart;
import com.risingcrescent.domain.entity.CartItem;
import com.risingcrescent.domain.entity.Product;
import com.risingcrescent.domain.entity.UserAccount;
import com.risingcrescent.domain.enums.Channel;
import com.risingcrescent.repo.CartRepository;
import com.risingcrescent.repo.ProductRepository;
import com.risingcrescent.repo.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CartService {
    private final CartRepository carts;
    private final UserAccountRepository users;
    private final ProductRepository products;
    private final AuditService audit;

    @Transactional
    public Map<String, Object> view(String username) {
        return snapshot(getOrCreate(username), users.findByUsername(username).orElseThrow());
    }

    @Transactional
    public Map<String, Object> add(String username, Long productId, BigDecimal qty) {
        if (qty == null || qty.signum() <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        Cart cart = getOrCreate(username);
        Product product = products.findById(productId).orElseThrow(() -> new IllegalArgumentException("Product not found"));
        cart.getItems().stream()
                .filter(i -> i.getProduct().getId().equals(productId))
                .findFirst()
                .ifPresentOrElse(i -> i.setQty(i.getQty().add(qty)),
                        () -> cart.getItems().add(CartItem.builder().cart(cart).product(product).qty(qty).build()));
        carts.save(cart);
        audit.record(username, "CART_ADD", "PRODUCT", String.valueOf(productId), "qty=" + qty);
        return snapshot(cart, users.findByUsername(username).orElseThrow());
    }

    @Transactional
    public Map<String, Object> update(String username, Long productId, BigDecimal qty) {
        Cart cart = getOrCreate(username);
        cart.getItems().removeIf(i -> i.getProduct().getId().equals(productId) && (qty == null || qty.signum() <= 0));
        cart.getItems().stream()
                .filter(i -> i.getProduct().getId().equals(productId))
                .findFirst()
                .ifPresent(i -> i.setQty(qty));
        carts.save(cart);
        return snapshot(cart, users.findByUsername(username).orElseThrow());
    }

    @Transactional
    public void clear(Cart cart) {
        cart.getItems().clear();
        carts.save(cart);
    }

    public Cart getOrCreate(String username) {
        UserAccount user = users.findByUsername(username).orElseThrow();
        return carts.findByOwner(user).orElseGet(() -> carts.save(Cart.builder().owner(user).build()));
    }

    public Map<String, Object> snapshot(Cart cart, UserAccount user) {
        boolean wholesale = user.getPreferredChannel() == Channel.WHOLESALE
                || user.getRoles().stream().anyMatch(r -> "SALES_EXECUTIVE".equals(r.getCode()));
        BigDecimal subtotal = BigDecimal.ZERO;
        var items = new java.util.ArrayList<Map<String, Object>>();
        for (CartItem item : cart.getItems()) {
            Product p = item.getProduct();
            BigDecimal price = wholesale ? p.getWholesalePrice() : p.getRetailPrice();
            BigDecimal line = price.multiply(item.getQty());
            subtotal = subtotal.add(line);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("productId", p.getId());
            row.put("sku", p.getSku());
            row.put("name", p.getName());
            row.put("uom", p.getUom());
            row.put("qty", item.getQty());
            row.put("unitPrice", price);
            row.put("lineTotal", line);
            row.put("imageHint", p.getImageHint());
            row.put("imageUrl", p.getImageUrl());
            items.add(row);
        }
        BigDecimal tax = subtotal.multiply(new BigDecimal("0.05"));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("items", items);
        m.put("itemCount", items.size());
        m.put("subtotal", subtotal);
        m.put("tax", tax);
        m.put("total", subtotal.add(tax));
        m.put("channel", wholesale ? Channel.WHOLESALE : Channel.RETAIL);
        return m;
    }
}
