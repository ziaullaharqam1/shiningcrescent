package com.shiningcrescent.integration.noon;

import com.shiningcrescent.config.NoonProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class HttpNoonLogisticsClient implements NoonLogisticsClient {
    private final RestClient noonRestClient;
    private final NoonProperties noon;

    @Override
    public DispatchResult dispatch(DispatchRequest request) {
        if (!noon.live()) {
            return local(request);
        }
        try {
            String warehouse = noon.warehouseCode() == null || noon.warehouseCode().isBlank()
                    ? request.warehouseCode() : noon.warehouseCode();
            Map<String, Object> awbBody = Map.of(
                    "warehouse_code", warehouse,
                    "awb_count", 1
            );
            Map<String, Object> awbResp = noonRestClient.post()
                    .uri("/v1/shipment/noon-logistics-awbs/get")
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(h -> h.setBearerAuth(noon.apiKey()))
                    .body(awbBody)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            String awb = extractAwb(awbResp);
            String shipmentNr = "RC-" + request.orderNo();
            String instructions = deliveryInstructions(request);
            Map<String, Object> shipment = new java.util.LinkedHashMap<>();
            shipment.put("warehouse_code", warehouse);
            shipment.put("integration_shipment_nr", shipmentNr);
            shipment.put("fbpi_order_nr", request.orderNo());
            shipment.put("awbs", List.of(Map.of("courier", "noon", "awb_nr", awb)));
            shipment.put("items", request.items() == null ? List.of() : request.items());
            shipment.put("payment_type", request.collectCash() ? "COD" : "PREPAID");
            if (request.collectCash()) {
                shipment.put("cod_amount", request.cashAmount() == null ? "0" : request.cashAmount());
            }
            if (!instructions.isBlank()) {
                shipment.put("delivery_instructions", instructions);
            }
            noonRestClient.post()
                    .uri("/v1/shipment/create")
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(h -> h.setBearerAuth(noon.apiKey()))
                    .body(shipment)
                    .retrieve()
                    .toBodilessEntity();
            return new DispatchResult(awb, shipmentNr, awb, "noon");
        } catch (Exception ex) {
            log.warn("Noon live dispatch failed, using local fleet: {}", ex.getMessage());
            DispatchResult local = local(request);
            return new DispatchResult(local.awb(), local.shipmentNr(), local.jobId(), "noon-local-fallback");
        }
    }

    private String extractAwb(Map<String, Object> awbResp) {
        if (awbResp == null) {
            return generatedAwb();
        }
        Object list = awbResp.get("awbs");
        if (list instanceof List<?> awbs && !awbs.isEmpty()) {
            Object first = awbs.get(0);
            if (first instanceof String s) return s;
            if (first instanceof Map<?, ?> m && m.get("awb_nr") != null) return String.valueOf(m.get("awb_nr"));
        }
        if (awbResp.get("awb_nr") != null) return String.valueOf(awbResp.get("awb_nr"));
        return generatedAwb();
    }

    private String deliveryInstructions(DispatchRequest request) {
        StringBuilder sb = new StringBuilder();
        if (request.leaveAtDoor()) {
            sb.append("Leave at the door. Do not knock. ");
        }
        if (request.collectCash()) {
            sb.append("Collect cash on delivery AED ").append(request.cashAmount() == null ? "" : request.cashAmount()).append(". ");
        }
        if (request.notes() != null && !request.notes().isBlank()) {
            sb.append(request.notes());
        }
        return sb.toString().trim();
    }

    private DispatchResult local(DispatchRequest request) {
        log.info("Noon local dispatch {} payment={} leaveAtDoor={} cash={}",
                request.orderNo(),
                request.collectCash() ? "COD" : "PREPAID",
                request.leaveAtDoor(),
                request.cashAmount());
        String awb = generatedAwb();
        return new DispatchResult(awb, "RC-" + request.orderNo(), awb, "noon-local");
    }

    private String generatedAwb() {
        return "NOON" + Instant.now().toEpochMilli();
    }
}
