package com.shiningcrescent.service;

import com.shiningcrescent.domain.enums.OrderStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeliveryTickJob {
    private final DeliveryService delivery;
    private final OrderService orders;

    @Scheduled(fixedDelayString = "${app.delivery.tick-ms:4000}")
    public void tick() {
        for (Long orderId : delivery.tick()) {
            try {
                orders.transition(orderId, OrderStatus.DELIVERED, "noon-rider", "Noon rider arrived");
            } catch (Exception ex) {
                log.debug("Order {} not moved to DELIVERED: {}", orderId, ex.getMessage());
            }
        }
    }
}
