package com.shiningcrescent.web;

import com.shiningcrescent.service.CartService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {
    private final CartService carts;

    public record QtyRequest(Long productId, BigDecimal qty) {}

    @GetMapping
    public Map<String, Object> get(Principal p) {
        return carts.view(p.getName());
    }

    @PostMapping("/items")
    public Map<String, Object> add(Principal p, @RequestBody QtyRequest req) {
        return carts.add(p.getName(), req.productId(), req.qty());
    }

    @PutMapping("/items")
    public Map<String, Object> update(Principal p, @RequestBody QtyRequest req) {
        return carts.update(p.getName(), req.productId(), req.qty());
    }
}
