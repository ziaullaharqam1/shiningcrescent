package com.risingcrescent.notify;

import com.risingcrescent.domain.entity.AppNotification;
import com.risingcrescent.event.DomainEvent;
import com.risingcrescent.repo.AppNotificationRepository;
import com.risingcrescent.repo.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class NotificationEngine {
    private final AppNotificationRepository notifications;
    private final UserAccountRepository users;

    @EventListener
    public void onDomainEvent(DomainEvent event) {
        handle(event);
    }

    public void handle(DomainEvent event) {
        Map<String, Object> p = event.payload();
        switch (event.type()) {
            case "ORDER_PLACED" -> notifyRoles(List.of("SALES_EXECUTIVE", "WAREHOUSE_KEEPER", "TRADING_MANAGER"),
                    "New sales order", "Order " + event.key() + " was placed.", "ORDER", event.key());
            case "ORDER_CREDIT_HOLD" -> notifyRoles(List.of("FINANCE_OFFICER", "TRADING_MANAGER"),
                    "Credit hold", "Order " + event.key() + " exceeds credit limit.", "ORDER", event.key());
            case "ORDER_CONFIRMED" -> notifyUser(str(p.get("customer")),
                    "Order confirmed", "Your order " + event.key() + " is confirmed and moving to the warehouse.", "ORDER", event.key());
            case "ORDER_SHIPPED" -> notifyUser(str(p.get("customer")),
                    "Order shipped", "Order " + event.key() + " is on the way with a Noon rider.", "ORDER", event.key());
            case "RIDER_ASSIGNED" -> notifyUser(str(p.get("customer")),
                    "Rider assigned", "A Noon rider is heading to you with order " + event.key() + ".", "ORDER", event.key());
            case "ORDER_DELIVERED" -> notifyUser(str(p.get("customer")),
                    "Delivered", "Order " + event.key() + " was delivered. Enjoy the harvest.", "ORDER", event.key());
            case "PO_APPROVED" -> notifyRoles(List.of("PROCUREMENT_OFFICER", "WAREHOUSE_KEEPER", "SUPPLIER"),
                    "Purchase order approved", "PO " + event.key() + " is approved.", "PO", event.key());
            case "LOT_QUARANTINE" -> notifyRoles(List.of("QUALITY_INSPECTOR"),
                    "Lot awaiting QC", "Lot " + event.key() + " is in quarantine.", "LOT", event.key());
            case "QC_PASSED" -> notifyRoles(List.of("WAREHOUSE_KEEPER", "PROCUREMENT_OFFICER"),
                    "QC passed", "Lot " + event.key() + " released to available stock.", "LOT", event.key());
            case "QC_REJECTED" -> notifyRoles(List.of("PROCUREMENT_OFFICER", "TRADING_MANAGER"),
                    "QC rejected", "Lot " + event.key() + " failed inspection.", "LOT", event.key());
            case "LOW_STOCK" -> notifyRoles(List.of("PROCUREMENT_OFFICER", "TRADING_MANAGER"),
                    "Low stock", str(p.get("product")) + " is below reorder cover.", "PRODUCT", event.key());
            case "PRODUCT_PENDING" -> notifyRoles(List.of("TRADING_MANAGER"),
                    "Catalog approval", "SKU " + event.key() + " awaits publish approval.", "PRODUCT", event.key());
            default -> {
            }
        }
    }

    public void notifyUser(String username, String title, String body, String refType, String refId) {
        if (username == null || username.isBlank()) {
            return;
        }
        notifications.save(AppNotification.builder()
                .recipientUsername(username)
                .title(title)
                .body(body)
                .type("INFO")
                .refType(refType)
                .refId(refId)
                .build());
    }

    public void notifyRoles(List<String> roleCodes, String title, String body, String refType, String refId) {
        users.findAll().stream()
                .filter(u -> u.getRoles().stream().anyMatch(r -> roleCodes.contains(r.getCode())))
                .forEach(u -> notifyUser(u.getUsername(), title, body, refType, refId));
    }

    public List<AppNotification> inbox(String username) {
        return notifications.findByRecipientUsernameOrderByCreatedAtDesc(username);
    }

    public long unread(String username) {
        return notifications.countByRecipientUsernameAndReadFlagFalse(username);
    }

    public void markRead(Long id, String username) {
        notifications.findById(id).ifPresent(n -> {
            if (username.equals(n.getRecipientUsername())) {
                n.setReadFlag(true);
                notifications.save(n);
            }
        });
    }

    private String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }
}
