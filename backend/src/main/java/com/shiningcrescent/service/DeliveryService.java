package com.shiningcrescent.service;

import com.shiningcrescent.audit.AuditService;
import com.shiningcrescent.config.DeliveryProperties;
import com.shiningcrescent.domain.entity.Delivery;
import com.shiningcrescent.domain.entity.Rider;
import com.shiningcrescent.domain.entity.SalesOrder;
import com.shiningcrescent.domain.enums.DeliveryStatus;
import com.shiningcrescent.event.DomainEvent;
import com.shiningcrescent.event.EventPublisher;
import com.shiningcrescent.integration.noon.NoonLogisticsClient;
import com.shiningcrescent.repo.DeliveryRepository;
import com.shiningcrescent.repo.RiderRepository;
import com.shiningcrescent.repo.SalesOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DeliveryService {
    private final DeliveryRepository deliveries;
    private final RiderRepository riders;
    private final SalesOrderRepository orders;
    private final NoonLogisticsClient noon;
    private final DeliveryProperties props;
    private final AuditService audit;
    private final EventPublisher events;
    private final StripeGateway stripe;

    @Transactional(noRollbackFor = IllegalStateException.class)
    public Delivery dispatch(SalesOrder order) {
        return deliveries.findByOrder_Id(order.getId()).orElseGet(() -> {
            String vehicleKind = vehicleKindFor(order);
            Rider rider = pickAvailable(order.getDropLat(), order.getDropLng(), vehicleKind);
            NoonLogisticsClient.DispatchResult job = noon.dispatch(new NoonLogisticsClient.DispatchRequest(
                    order.getOrderNo(),
                    "DXB-COLD",
                    order.getShipToName(),
                    order.getShipToPhone(),
                    order.getShipToAddress(),
                    n(order.getDropLat(), 25.1972),
                    n(order.getDropLng(), 55.2744),
                    order.getLines().stream().map(l -> Map.of(
                            "mp_item_nr", order.getOrderNo() + "-" + l.getProduct().getSku(),
                            "partner_sku", l.getProduct().getSku()
                    )).toList(),
                    "CASH_ON_DELIVERY".equals(order.getPaymentMethod()),
                    order.getTotal() == null ? "0" : order.getTotal().toPlainString(),
                    order.isLeaveAtDoor(),
                    order.getNotes()
            ));
            double pickLat = props.warehouseLat();
            double pickLng = props.warehouseLng();
            double dropLat = n(order.getDropLat(), 25.1972);
            double dropLng = n(order.getDropLng(), 55.2744);
            int eta = GeoUtil.etaMinutes(GeoUtil.km(pickLat, pickLng, dropLat, dropLng));
            rider.setOnJob(true);
            rider.setAvailable(false);
            rider.setLastLat(pickLat);
            rider.setLastLng(pickLng);
            riders.save(rider);
            Delivery d = deliveries.save(Delivery.builder()
                    .order(order)
                    .rider(rider)
                    .status(DeliveryStatus.EN_ROUTE)
                    .provider(job.source())
                    .noonAwb(job.awb())
                    .noonShipmentNr(job.shipmentNr())
                    .noonJobId(job.jobId())
                    .pickupLat(pickLat)
                    .pickupLng(pickLng)
                    .dropLat(dropLat)
                    .dropLng(dropLng)
                    .riderLat(pickLat)
                    .riderLng(pickLng)
                    .vehicleKind(vehicleKind)
                    .etaMinutes(eta)
                    .etaAt(Instant.now().plusSeconds(eta * 60L))
                    .assignedAt(Instant.now())
                    .departedAt(Instant.now())
                    .build());
            audit.record("noon", "RIDER_ASSIGNED", "SALES_ORDER", order.getOrderNo(),
                    rider.getName() + " AWB=" + job.awb());
            events.publish(DomainEvent.of("rc.orders", "RIDER_ASSIGNED", order.getOrderNo(),
                    Map.of("customer", order.getCustomer().getUsername(), "rider", rider.getName())));
            return d;
        });
    }

    private String vehicleKindFor(SalesOrder order) {
        BigDecimal qty = order.getLines().stream()
                .map(l -> l.getQty() == null ? BigDecimal.ZERO : l.getQty())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (order.getChannel() == com.shiningcrescent.domain.enums.Channel.WHOLESALE
                || qty.compareTo(new BigDecimal("12")) >= 0
                || order.getLines().size() >= 5) {
            return "CAR";
        }
        return "SCOOTER";
    }

    private Rider pickAvailable(Double dropLat, Double dropLng, String vehicleKind) {
        double lat = n(dropLat, props.warehouseLat());
        double lng = n(dropLng, props.warehouseLng());
        List<Rider> free = riders.findByAvailableTrueAndOnJobFalse();
        List<Rider> matched = free.stream().filter(r -> matchesVehicle(r, vehicleKind)).toList();
        List<Rider> pool = matched.isEmpty() ? free : matched;
        return pool.stream()
                .min(Comparator
                        .comparingDouble((Rider r) -> GeoUtil.km(
                                n(r.getLastLat(), props.warehouseLat()),
                                n(r.getLastLng(), props.warehouseLng()),
                                lat, lng))
                        .thenComparing(Comparator.comparing(Rider::getRating).reversed()))
                .orElseThrow(() -> new IllegalStateException("No Noon riders are available right now"));
    }

    private static boolean matchesVehicle(Rider r, String kind) {
        String v = r.getVehicle() == null ? "" : r.getVehicle().toLowerCase();
        if ("CAR".equals(kind)) {
            return v.contains("van") || v.contains("car") || v.contains("truck");
        }
        return v.contains("bike") || v.contains("scooter") || v.contains("cycle");
    }

    @Transactional(readOnly = true)
    public Map<String, Object> tracking(Long orderId, String username, boolean staff) {
        SalesOrder order = orders.findById(orderId).orElseThrow(() -> new IllegalArgumentException("Order not found"));
        if (!staff && !order.getCustomer().getUsername().equals(username)) {
            throw new IllegalArgumentException("Order not found");
        }
        Delivery d = deliveries.findByOrder_Id(orderId).orElse(null);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("orderId", order.getId());
        m.put("orderNo", order.getOrderNo());
        m.put("orderStatus", order.getStatus());
        m.put("paymentStatus", order.getPaymentStatus());
        if (d == null) {
            m.put("assigned", false);
            return m;
        }
        m.put("assigned", true);
        m.putAll(view(d));
        return m;
    }

    @Transactional
    public List<Long> tick() {
        List<Long> arrivedOrderIds = new java.util.ArrayList<>();
        for (Delivery d : deliveries.findByStatusIn(List.of(
                DeliveryStatus.ASSIGNED, DeliveryStatus.AT_WAREHOUSE, DeliveryStatus.EN_ROUTE, DeliveryStatus.ARRIVING))) {
            if (d.getDepartedAt() == null || d.getEtaMinutes() == null || d.getEtaMinutes() <= 0) continue;
            long elapsed = Duration.between(d.getDepartedAt(), Instant.now()).getSeconds();
            double t = Math.min(1.0, (elapsed * 18.0) / (d.getEtaMinutes() * 60.0));
            d.setRiderLat(GeoUtil.lerp(d.getPickupLat(), d.getDropLat(), t));
            d.setRiderLng(GeoUtil.lerp(d.getPickupLng(), d.getDropLng(), t));
            int remaining = (int) Math.max(0, Math.round(d.getEtaMinutes() * (1 - t)));
            d.setEtaAt(Instant.now().plusSeconds(remaining * 60L));
            if (t >= 0.92 && d.getStatus() != DeliveryStatus.ARRIVING && t < 1) {
                d.setStatus(DeliveryStatus.ARRIVING);
            }
            if (t >= 1) {
                complete(d);
                arrivedOrderIds.add(d.getOrder().getId());
            } else {
                if (d.getRider() != null) {
                    d.getRider().setLastLat(d.getRiderLat());
                    d.getRider().setLastLng(d.getRiderLng());
                }
                deliveries.save(d);
            }
        }
        return arrivedOrderIds;
    }

    private void complete(Delivery d) {
        d.setStatus(DeliveryStatus.DELIVERED);
        d.setRiderLat(d.getDropLat());
        d.setRiderLng(d.getDropLng());
        d.setDeliveredAt(Instant.now());
        d.setEtaAt(Instant.now());
        Rider rider = d.getRider();
        if (rider != null) {
            rider.setOnJob(false);
            rider.setAvailable(true);
            rider.setJobsCompleted(rider.getJobsCompleted() + 1);
            rider.setLastLat(d.getDropLat());
            rider.setLastLng(d.getDropLng());
            riders.save(rider);
        }
        deliveries.save(d);
    }

    @Transactional
    public Map<String, Object> tip(Long orderId, BigDecimal amount, String username) {
        Delivery d = requireOwn(orderId, username);
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Tip amount must be greater than zero");
        }
        if (d.getStatus() != DeliveryStatus.DELIVERED && d.getStatus() != DeliveryStatus.ARRIVING) {
            throw new IllegalStateException("Tip the rider after they arrive");
        }
        SalesOrder order = d.getOrder();
        Map<String, Object> out = new LinkedHashMap<>();
        if (stripe.mockMode()) {
            d.setTipAmount(d.getTipAmount().add(amount));
            deliveries.save(d);
            audit.record(username, "RIDER_TIP", "SALES_ORDER", order.getOrderNo(), amount.toPlainString());
            out.put("mock", true);
            out.put("tipAmount", d.getTipAmount());
            out.putAll(view(d));
            return out;
        }
        var pi = stripe.createPaymentIntent(order.getOrderNo(), amount, "rider-tip");
        order.setStripeTipIntentId(pi.getId());
        orders.save(order);
        out.put("mock", false);
        out.put("clientSecret", pi.getClientSecret());
        out.put("paymentIntentId", pi.getId());
        out.put("tipAmountPending", amount);
        return out;
    }

    @Transactional
    public Map<String, Object> confirmTip(Long orderId, BigDecimal amount, String username) {
        Delivery d = requireOwn(orderId, username);
        d.setTipAmount(d.getTipAmount().add(amount));
        deliveries.save(d);
        audit.record(username, "RIDER_TIP", "SALES_ORDER", d.getOrder().getOrderNo(), amount.toPlainString());
        return view(d);
    }

    @Transactional
    public Map<String, Object> feedback(Long orderId, int rating, String comment, String username) {
        Delivery d = requireOwn(orderId, username);
        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("Rating must be 1 to 5");
        }
        d.setCustomerRating(rating);
        d.setFeedback(comment);
        Rider rider = d.getRider();
        if (rider != null) {
            BigDecimal prev = rider.getRating() == null ? new BigDecimal("4.80") : rider.getRating();
            int n = Math.max(1, rider.getJobsCompleted());
            rider.setRating(prev.multiply(BigDecimal.valueOf(n))
                    .add(BigDecimal.valueOf(rating))
                    .divide(BigDecimal.valueOf(n + 1), 2, java.math.RoundingMode.HALF_UP));
            riders.save(rider);
        }
        deliveries.save(d);
        audit.record(username, "RIDER_FEEDBACK", "SALES_ORDER", d.getOrder().getOrderNo(), rating + " " + comment);
        return view(d);
    }

    private Delivery requireOwn(Long orderId, String username) {
        SalesOrder order = orders.findById(orderId).orElseThrow(() -> new IllegalArgumentException("Order not found"));
        if (!order.getCustomer().getUsername().equals(username)) {
            throw new IllegalArgumentException("Order not found");
        }
        return deliveries.findByOrder_Id(orderId).orElseThrow(() -> new IllegalArgumentException("Delivery not assigned yet"));
    }

    public Map<String, Object> view(Delivery d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("deliveryId", d.getId());
        m.put("status", d.getStatus());
        m.put("provider", d.getProvider());
        m.put("noonAwb", d.getNoonAwb());
        m.put("etaAt", d.getEtaAt());
        m.put("pickupLat", d.getPickupLat());
        m.put("pickupLng", d.getPickupLng());
        m.put("dropLat", d.getDropLat());
        m.put("dropLng", d.getDropLng());
        m.put("riderLat", d.getRiderLat());
        m.put("riderLng", d.getRiderLng());
        String kind = resolveVehicleKind(d);
        m.put("vehicleKind", kind);
        double distKm = 0;
        if (d.getRiderLat() != null && d.getDropLat() != null) {
            distKm = GeoUtil.km(d.getRiderLat(), n(d.getRiderLng(), 0), d.getDropLat(), n(d.getDropLng(), 0));
        }
        m.put("distanceKm", Math.round(distKm * 1000.0) / 1000.0);
        m.put("distanceMeters", (int) Math.round(distKm * 1000));
        int eta = remainingMinutes(d);
        boolean arrived = d.getStatus() == DeliveryStatus.DELIVERED || (eta == 0 && d.getStatus() == DeliveryStatus.ARRIVING);
        m.put("arrived", arrived);
        m.put("deliveryPhase", arrived ? "Arrived" : (d.getStatus() == DeliveryStatus.ARRIVING ? "Arriving" : statusEnglish(d.getStatus())));
        if (arrived) {
            m.put("distanceLabel", "Arrived");
            m.put("etaMinutes", 0);
        } else {
            m.put("distanceLabel", GeoUtil.formatDistance(distKm));
            m.put("etaMinutes", eta);
        }
        m.put("collectCash", "CASH_ON_DELIVERY".equals(d.getOrder() == null ? "" : d.getOrder().getPaymentMethod()));
        m.put("leaveAtDoor", d.getOrder() != null && d.getOrder().isLeaveAtDoor());
        m.put("customerRating", d.getCustomerRating());
        m.put("feedback", d.getFeedback());
        Rider r = d.getRider();
        if (r != null) {
            m.put("rider", Map.of(
                    "id", r.getId(),
                    "name", r.getName(),
                    "phone", r.getPhone() == null ? "" : r.getPhone(),
                    "vehicle", r.getVehicle() == null ? "Noon bike" : r.getVehicle(),
                    "rating", r.getRating(),
                    "jobsCompleted", r.getJobsCompleted(),
                    "noonRiderCode", r.getNoonRiderCode() == null ? "" : r.getNoonRiderCode(),
                    "vehicleKind", kind
            ));
        }
        return m;
    }

    private static String resolveVehicleKind(Delivery d) {
        if (d.getVehicleKind() != null && !d.getVehicleKind().isBlank()) {
            return d.getVehicleKind();
        }
        Rider r = d.getRider();
        if (r != null && r.getVehicle() != null) {
            String v = r.getVehicle().toLowerCase();
            if (v.contains("van") || v.contains("car") || v.contains("truck")) return "CAR";
        }
        return "SCOOTER";
    }

    private static String statusEnglish(DeliveryStatus status) {
        if (status == null) return "";
        return switch (status) {
            case QUEUED -> "Queued";
            case ASSIGNED -> "Assigned";
            case AT_WAREHOUSE -> "At warehouse";
            case EN_ROUTE -> "En route";
            case ARRIVING -> "Arriving";
            case DELIVERED -> "Arrived";
            case CANCELLED -> "Cancelled";
        };
    }

    private int remainingMinutes(Delivery d) {
        if (d.getStatus() == DeliveryStatus.DELIVERED) return 0;
        if (d.getEtaAt() == null) return d.getEtaMinutes() == null ? 0 : d.getEtaMinutes();
        long sec = Duration.between(Instant.now(), d.getEtaAt()).getSeconds();
        return (int) Math.max(0, Math.round(sec / 60.0));
    }

    private static double n(Double v, double fallback) {
        return v == null ? fallback : v;
    }
}
