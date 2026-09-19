package com.risingcrescent.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.AreaReference;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFTable;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.CTTable;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.CTTableStyleInfo;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ExcelReportService {
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.of("Asia/Dubai"));

    public byte[] workbook(Map<String, Object> report) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle title = wb.createCellStyle();
            Font titleFont = wb.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 16);
            titleFont.setColor(IndexedColors.DARK_GREEN.getIndex());
            title.setFont(titleFont);

            CellStyle header = wb.createCellStyle();
            Font headerFont = wb.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            header.setFont(headerFont);
            header.setFillForegroundColor(IndexedColors.GREEN.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            header.setBorderBottom(BorderStyle.THIN);
            header.setBorderTop(BorderStyle.THIN);
            header.setBorderLeft(BorderStyle.THIN);
            header.setBorderRight(BorderStyle.THIN);

            CellStyle money = wb.createCellStyle();
            money.setDataFormat(wb.createDataFormat().getFormat("#,##0.00"));

            CellStyle wrap = wb.createCellStyle();
            wrap.setWrapText(true);

            XSSFSheet summary = wb.createSheet("Summary");
            int r = 0;
            Row t = summary.createRow(r++);
            t.createCell(0).setCellValue("Rising Crescent — " + kindLabel(report) + " report");
            t.getCell(0).setCellStyle(title);
            summary.createRow(r++).createCell(0).setCellValue("Period: " + String.valueOf(report.get("label")));
            summary.createRow(r++).createCell(0).setCellValue("Timezone: " + String.valueOf(report.getOrDefault("timezone", "Asia/Dubai")));
            r++;
            Map<String, Object> kpis = new LinkedHashMap<>();
            kpis.put("Sales orders", report.get("orderCount"));
            kpis.put("Delivered", report.get("deliveredCount"));
            kpis.put("Cancelled", report.get("cancelledCount"));
            kpis.put("Sales total (AED)", report.get("salesTotal"));
            kpis.put("Invoices", report.get("invoiceCount"));
            kpis.put("Invoiced (AED)", report.get("invoiceTotal"));
            kpis.put("Collected (AED)", report.get("invoicePaid"));
            kpis.put("Purchase orders", report.get("poCount"));
            kpis.put("PO value (AED)", report.get("poTotal"));
            kpis.put("QC inspections", report.get("qcCount"));
            writeTable(summary, r, List.of("Metric", "Value"), kpis.entrySet().stream()
                    .map(e -> List.of(e.getKey(), e.getValue()))
                    .toList(), header, money, "SummaryKpis");
            summary.setColumnWidth(0, 28 * 256);
            summary.setColumnWidth(1, 22 * 256);

            writeDetail(wb, "Sales orders", List.of("Order", "Customer", "Status", "Payment", "Total", "Placed"),
                    rows(report.get("orders"), List.of("orderNo", "customer", "status", "paymentStatus", "total", "placedAt")),
                    header, money);
            writeDetail(wb, "Invoices", List.of("Invoice", "Order", "Customer", "Amount", "Paid", "Status", "Issued"),
                    rows(report.get("invoices"), List.of("invoiceNo", "orderNo", "customer", "amount", "paidAmount", "status", "issuedAt")),
                    header, money);
            writeDetail(wb, "Purchase orders", List.of("PO", "Supplier", "Warehouse", "Status", "Total", "Created", "Lines"),
                    rows(report.get("purchaseOrders"), List.of("poNo", "supplier", "warehouse", "status", "total", "createdAt", "lines")),
                    header, money);
            writeDetail(wb, "Quality", List.of("QC", "Lot", "Product", "Result", "Inspector", "Inspected"),
                    rows(report.get("quality"), List.of("inspectionNo", "lotCode", "product", "result", "inspector", "inspectedAt")),
                    header, money);

            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Could not build the Excel report.");
        }
    }

    private String kindLabel(Map<String, Object> report) {
        return "MONTHLY".equals(String.valueOf(report.get("kind"))) ? "Monthly" : "Daily";
    }

    @SuppressWarnings("unchecked")
    private List<List<Object>> rows(Object raw, List<String> keys) {
        if (!(raw instanceof Collection<?> list)) {
            return List.of();
        }
        return list.stream().map(item -> {
            Map<String, Object> m = item instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
            return keys.stream().map(m::get).map(this::cell).toList();
        }).toList();
    }

    private Object cell(Object v) {
        if (v == null) return "";
        if (v instanceof Instant i) return TS.format(i);
        return v;
    }

    private void writeDetail(XSSFWorkbook wb, String name, List<String> headers, List<List<Object>> data,
                             CellStyle header, CellStyle money) {
        XSSFSheet sheet = wb.createSheet(name);
        writeTable(sheet, 0, headers, data, header, money, name.replace(" ", "") + "Tbl");
        for (int i = 0; i < headers.size(); i++) {
            sheet.autoSizeColumn(i);
            int w = Math.min(Math.max(sheet.getColumnWidth(i), 14 * 256), 36 * 256);
            sheet.setColumnWidth(i, w);
        }
        sheet.createFreezePane(0, 1);
    }

    private void writeTable(XSSFSheet sheet, int startRow, List<String> headers, List<List<Object>> data,
                            CellStyle header, CellStyle money, String tableName) {
        Row head = sheet.createRow(startRow);
        for (int i = 0; i < headers.size(); i++) {
            Cell c = head.createCell(i);
            c.setCellValue(headers.get(i));
            c.setCellStyle(header);
        }
        int rowIdx = startRow + 1;
        if (data.isEmpty()) {
            Row empty = sheet.createRow(rowIdx);
            empty.createCell(0).setCellValue("No rows in this period");
            rowIdx++;
        } else {
            for (List<Object> row : data) {
                Row excel = sheet.createRow(rowIdx++);
                for (int i = 0; i < headers.size(); i++) {
                    Object v = i < row.size() ? row.get(i) : "";
                    Cell cell = excel.createCell(i);
                    if (v instanceof Number n) {
                        cell.setCellValue(n.doubleValue());
                        String h = headers.get(i).toLowerCase();
                        if (h.contains("total") || h.contains("amount") || h.contains("paid") || h.contains("aed") || h.contains("value")) {
                            cell.setCellStyle(money);
                        }
                    } else {
                        cell.setCellValue(String.valueOf(v == null ? "" : v));
                    }
                }
            }
        }
        int lastRow = Math.max(startRow + 1, rowIdx - 1);
        int lastCol = headers.size() - 1;
            try {
                AreaReference area = new AreaReference(
                        new CellReference(startRow, 0),
                        new CellReference(lastRow, lastCol),
                        sheet.getWorkbook().getSpreadsheetVersion());
                XSSFTable table = sheet.createTable(area);
                table.setName(tableName);
                table.setDisplayName(tableName);
                CTTable ct = table.getCTTable();
                ct.setId(sheet.getWorkbook().getNumberOfSheets() + sheet.getTables().size());
                ct.setDisplayName(tableName);
                ct.setName(tableName);
                ct.setRef(area.formatAsString());
                if (ct.getTableStyleInfo() == null) {
                    ct.addNewTableStyleInfo();
                }
                CTTableStyleInfo style = ct.getTableStyleInfo();
                style.setName("TableStyleMedium2");
                style.setShowRowStripes(true);
                style.setShowColumnStripes(false);
                if (ct.getAutoFilter() == null) {
                    ct.addNewAutoFilter();
                }
                ct.getAutoFilter().setRef(area.formatAsString());
            } catch (Exception ignored) {
                sheet.setAutoFilter(org.apache.poi.ss.util.CellRangeAddress.valueOf(
                        new CellReference(startRow, 0).formatAsString() + ":" + new CellReference(lastRow, lastCol).formatAsString()));
            }
    }
}
