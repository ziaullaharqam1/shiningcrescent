package com.risingcrescent.integration.noon;

import java.util.List;
import java.util.Map;

public interface NoonLogisticsClient {
    DispatchResult dispatch(DispatchRequest request);

    record DispatchRequest(
            String orderNo,
            String warehouseCode,
            String shipToName,
            String shipToPhone,
            String shipToAddress,
            double dropLat,
            double dropLng,
            List<Map<String, String>> items,
            boolean collectCash,
            String cashAmount,
            boolean leaveAtDoor,
            String notes
    ) {}

    record DispatchResult(String awb, String shipmentNr, String jobId, String source) {}
}
