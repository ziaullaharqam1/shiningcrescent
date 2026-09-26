package com.shiningcrescent.web;

import com.shiningcrescent.audit.AuditService;
import com.shiningcrescent.domain.enums.InspectionResult;
import com.shiningcrescent.domain.enums.PurchaseStatus;
import com.shiningcrescent.domain.enums.WorkflowEntity;
import com.shiningcrescent.notify.NotificationEngine;
import com.shiningcrescent.security.RoleChecks;
import com.shiningcrescent.service.*;
import com.shiningcrescent.workflow.WorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/console")
@RequiredArgsConstructor
public class ConsoleController {
    private final DashboardService dashboard;
    private final MasterService masters;
    private final ProcurementService procurement;
    private final InventoryService inventory;
    private final FinanceService finance;
    private final NotificationEngine notifications;
    private final AuditService audit;
    private final WorkflowService workflow;
    private final ReportService reports;
    private final ExcelReportService excel;
    private final DeletionService deletions;

    @GetMapping("/dashboard")
    @PreAuthorize("hasAuthority('DASHBOARD:VIEW')")
    public Map<String, Object> dashboard(Principal p, Authentication auth) {
        if (RoleChecks.supplierPortal(auth)) {
            return dashboard.supplierSnapshot(p.getName());
        }
        return dashboard.snapshot();
    }

    @GetMapping("/categories")
    @PreAuthorize("hasAuthority('CATEGORIES:VIEW')")
    public Object categories() {
        return masters.listCategories();
    }

    @PostMapping("/categories")
    @PreAuthorize("hasAnyAuthority('CATEGORIES:CREATE','CATEGORIES:UPDATE')")
    public Object saveCategory(@RequestBody Map<String, Object> body, Principal p) {
        return masters.saveCategory(body, p.getName());
    }

    @DeleteMapping("/categories/{id}")
    @PreAuthorize("hasAnyAuthority('CATEGORIES:DELETE','CATEGORIES:UPDATE')")
    public void deleteCategory(@PathVariable Long id, Principal p) {
        deletions.deleteCategory(id, p.getName());
    }

    @GetMapping("/products")
    @PreAuthorize("hasAuthority('PRODUCTS:VIEW')")
    public Object products() {
        return masters.listProducts();
    }

    @PostMapping("/products")
    @PreAuthorize("hasAnyAuthority('PRODUCTS:CREATE','PRODUCTS:UPDATE')")
    public Object saveProduct(@RequestBody Map<String, Object> body, Principal p) {
        return masters.saveProduct(body, p.getName());
    }

    @PostMapping("/products/{id}/image")
    @PreAuthorize("hasAnyAuthority('PRODUCTS:CREATE','PRODUCTS:UPDATE')")
    public Object productImage(@PathVariable Long id, @RequestParam("file") MultipartFile file, Principal p) {
        return masters.saveProductImage(id, file, p.getName());
    }

    @PostMapping("/products/{id}/submit")
    @PreAuthorize("hasAuthority('PRODUCTS:UPDATE')")
    public Object submitProduct(@PathVariable Long id, Principal p) {
        return masters.submitProduct(id, p.getName());
    }

    @PostMapping("/products/{id}/publish")
    @PreAuthorize("hasAuthority('PRODUCTS:APPROVE')")
    public Object publish(@PathVariable Long id, @RequestParam(defaultValue = "true") boolean approve, Principal p) {
        return masters.publishProduct(id, p.getName(), approve);
    }

    @DeleteMapping("/products/{id}")
    @PreAuthorize("hasAnyAuthority('PRODUCTS:DELETE','PRODUCTS:UPDATE')")
    public void deleteProduct(@PathVariable Long id, Principal p) {
        deletions.deleteProduct(id, p.getName());
    }

    @GetMapping("/warehouses")
    @PreAuthorize("hasAuthority('WAREHOUSES:VIEW')")
    public Object warehouses() {
        return masters.listWarehouses();
    }

    @PostMapping("/warehouses")
    @PreAuthorize("hasAnyAuthority('WAREHOUSES:CREATE','WAREHOUSES:UPDATE')")
    public Object saveWarehouse(@RequestBody Map<String, Object> body, Principal p) {
        return masters.saveWarehouse(body, p.getName());
    }

    @DeleteMapping("/warehouses/{id}")
    @PreAuthorize("hasAnyAuthority('WAREHOUSES:DELETE','WAREHOUSES:UPDATE')")
    public void deleteWarehouse(@PathVariable Long id, Principal p) {
        deletions.deleteWarehouse(id, p.getName());
    }

    @GetMapping("/suppliers")
    @PreAuthorize("hasAuthority('SUPPLIERS:VIEW')")
    public Object suppliers() {
        return masters.listSuppliers();
    }

    @PostMapping("/suppliers")
    @PreAuthorize("hasAnyAuthority('SUPPLIERS:CREATE','SUPPLIERS:UPDATE')")
    public Object saveSupplier(@RequestBody Map<String, Object> body, Principal p) {
        return masters.saveSupplier(body, p.getName());
    }

    @DeleteMapping("/suppliers/{id}")
    @PreAuthorize("hasAnyAuthority('SUPPLIERS:DELETE','SUPPLIERS:UPDATE')")
    public void deleteSupplier(@PathVariable Long id, Principal p) {
        deletions.deleteSupplier(id, p.getName());
    }

    @GetMapping("/users")
    @PreAuthorize("hasAuthority('USERS:VIEW')")
    public Object users() {
        return masters.listUsers();
    }

    @PostMapping("/users")
    @PreAuthorize("hasAnyAuthority('USERS:CREATE','USERS:UPDATE')")
    public Object saveUser(@RequestBody Map<String, Object> body, Principal p) {
        return masters.saveUser(body, p.getName());
    }

    @DeleteMapping("/users/{id}")
    @PreAuthorize("hasAnyAuthority('USERS:DELETE','USERS:UPDATE')")
    public void deleteUser(@PathVariable Long id, Principal p) {
        deletions.deleteUser(id, p.getName());
    }

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('ROLES:VIEW')")
    public Object roles() {
        return masters.listRoles();
    }

    @GetMapping("/role-options")
    @PreAuthorize("hasAnyAuthority('USERS:VIEW','USERS:CREATE','USERS:UPDATE','ROLES:VIEW')")
    public Object roleOptions() {
        return masters.listRoleOptions();
    }

    @PutMapping("/roles/{id}/permissions")
    @PreAuthorize("hasAuthority('ROLES:UPDATE')")
    public Object rolePerms(@PathVariable Long id, @RequestBody List<String> permissions, Principal p) {
        return masters.saveRolePermissions(id, permissions, p.getName());
    }

    @GetMapping("/screens")
    @PreAuthorize("hasAuthority('ROLES:VIEW')")
    public Object screens() {
        return masters.listScreens();
    }

    @GetMapping("/purchase-orders")
    @PreAuthorize("hasAuthority('PURCHASE_ORDERS:VIEW')")
    public Object pos(Principal p, Authentication auth) {
        return procurement.listPos(p.getName(), RoleChecks.supplierPortal(auth));
    }

    @PostMapping("/purchase-orders")
    @PreAuthorize("hasAuthority('PURCHASE_ORDERS:CREATE')")
    public Object createPo(@RequestBody Map<String, Object> body, Principal p) {
        return procurement.createPo(p.getName(), body);
    }

    @PutMapping("/purchase-orders/{id}")
    @PreAuthorize("hasAnyAuthority('PURCHASE_ORDERS:UPDATE','PURCHASE_ORDERS:CREATE')")
    public Object editPo(@PathVariable Long id, @RequestBody Map<String, Object> body, Principal p, Authentication auth) {
        if (RoleChecks.supplierPortal(auth)) {
            Map<String, Object> allowed = new java.util.LinkedHashMap<>();
            if (body.get("notes") != null) allowed.put("notes", body.get("notes"));
            if (body.get("expectedDate") != null) allowed.put("expectedDate", body.get("expectedDate"));
            return procurement.updatePo(id, allowed, p.getName());
        }
        return procurement.updatePo(id, body, p.getName());
    }

    @DeleteMapping("/purchase-orders/{id}")
    @PreAuthorize("hasAnyAuthority('PURCHASE_ORDERS:DELETE','PURCHASE_ORDERS:UPDATE')")
    public void deletePo(@PathVariable Long id, Principal p, Authentication auth) {
        if (RoleChecks.supplierPortal(auth)) {
            throw new IllegalArgumentException("Suppliers cannot delete purchase orders.");
        }
        deletions.deletePurchaseOrder(id, p.getName());
    }

    @PostMapping("/purchase-orders/{id}/transition")
    @PreAuthorize("hasAnyAuthority('PURCHASE_ORDERS:UPDATE','PURCHASE_ORDERS:APPROVE')")
    public Object movePo(@PathVariable Long id, @RequestBody Map<String, String> body, Principal p,
                         Authentication auth) {
        PurchaseStatus status = PurchaseStatus.valueOf(body.get("status"));
        if (RoleChecks.supplierPortal(auth) && status != PurchaseStatus.ACKNOWLEDGED) {
            throw new IllegalArgumentException("Suppliers can only acknowledge assigned purchase orders.");
        }
        return procurement.movePo(id, status, p.getName(), body.get("comment"));
    }

    @PostMapping("/purchase-orders/{id}/approve-or-quarantine")
    @PreAuthorize("hasAnyAuthority('PURCHASE_ORDERS:APPROVE','PURCHASE_ORDERS:UPDATE','QUALITY:APPROVE','INVENTORY:UPDATE')")
    public Object approveOrQuarantine(@PathVariable Long id, @RequestBody Map<String, String> body, Principal p,
                                      Authentication auth) {
        if (RoleChecks.supplierPortal(auth)) {
            throw new IllegalArgumentException("Suppliers cannot approve or quarantine purchase orders.");
        }
        return procurement.approveOrQuarantine(id, body.get("action"), p.getName(), body.get("comment"));
    }

    @PostMapping("/purchase-orders/{id}/receive")
    @PreAuthorize("hasAuthority('INVENTORY:UPDATE')")
    public Object receive(@PathVariable Long id, @RequestBody Map<String, Object> body, Principal p) {
        return procurement.receive(id, Long.valueOf(body.get("productId").toString()),
                new BigDecimal(body.get("qty").toString()), p.getName());
    }

    @GetMapping("/quality")
    @PreAuthorize("hasAuthority('QUALITY:VIEW')")
    public Object quality(Principal p, Authentication auth) {
        return procurement.listInspections(p.getName(), RoleChecks.supplierPortal(auth));
    }

    @PostMapping("/quality/{id}/inspect")
    @PreAuthorize("hasAnyAuthority('QUALITY:APPROVE','QUALITY:UPDATE')")
    public Object inspect(@PathVariable Long id, @RequestBody Map<String, Object> body, Principal p) {
        return procurement.inspect(id,
                InspectionResult.valueOf(body.get("result").toString()),
                body.get("moisturePct") == null || body.get("moisturePct").toString().isBlank()
                        ? null : new BigDecimal(body.get("moisturePct").toString()),
                (String) body.get("appearance"),
                (String) body.get("foreignMatter"),
                (String) body.get("remarks"),
                p.getName());
    }

    @DeleteMapping("/quality/{id}")
    @PreAuthorize("hasAnyAuthority('QUALITY:DELETE','QUALITY:UPDATE','QUALITY:APPROVE')")
    public void deleteQuality(@PathVariable Long id, Principal p) {
        deletions.deleteInspection(id, p.getName());
    }

    @GetMapping("/inventory/lookups")
    @PreAuthorize("hasAnyAuthority('INVENTORY:CREATE','INVENTORY:UPDATE')")
    public Object inventoryLookups() {
        return inventory.lookups();
    }

    @GetMapping("/inventory")
    @PreAuthorize("hasAuthority('INVENTORY:VIEW')")
    public Object inventory(Principal p, Authentication auth) {
        if (RoleChecks.supplierPortal(auth)) {
            return inventory.forSupplier(p.getName());
        }
        return inventory.all();
    }

    @PostMapping("/inventory")
    @PreAuthorize("hasAnyAuthority('INVENTORY:CREATE','INVENTORY:UPDATE')")
    public Object createLot(@RequestBody Map<String, Object> body, Principal p) {
        return inventory.create(body, p.getName());
    }

    @PutMapping("/inventory/{id}")
    @PreAuthorize("hasAuthority('INVENTORY:UPDATE')")
    public Object updateLot(@PathVariable Long id, @RequestBody Map<String, Object> body, Principal p) {
        return inventory.update(id, body, p.getName());
    }

    @DeleteMapping("/inventory/{id}")
    @PreAuthorize("hasAnyAuthority('INVENTORY:DELETE','INVENTORY:UPDATE')")
    public void deleteLot(@PathVariable Long id, Principal p) {
        deletions.deleteLot(id, p.getName());
    }

    @GetMapping("/invoices")
    @PreAuthorize("hasAuthority('INVOICES:VIEW')")
    public Object invoices() {
        return finance.listInvoices();
    }

    @PostMapping("/invoices/{id}/pay")
    @PreAuthorize("hasAuthority('INVOICES:UPDATE')")
    public Object pay(@PathVariable Long id, @RequestBody Map<String, Object> body, Principal p) {
        return finance.pay(id, new BigDecimal(body.get("amount").toString()),
                String.valueOf(body.getOrDefault("method", "BANK")),
                (String) body.get("reference"), p.getName());
    }

    @GetMapping("/notifications")
    public Object inbox(Principal p) {
        return Map.of("items", notifications.inbox(p.getName()), "unread", notifications.unread(p.getName()));
    }

    @PostMapping("/notifications/{id}/read")
    public void read(@PathVariable Long id, Principal p) {
        notifications.markRead(id, p.getName());
    }

    @GetMapping("/audit")
    @PreAuthorize("hasAuthority('AUDIT:VIEW')")
    public Object auditLog() {
        return audit.recent();
    }

    @GetMapping("/workflows")
    @PreAuthorize("hasAuthority('WORKFLOWS:VIEW')")
    public Object workflows(@RequestParam(required = false) String entity,
                            @RequestParam(required = false) String ref) {
        if (entity != null && ref != null) {
            return workflow.trail(WorkflowEntity.valueOf(entity), ref);
        }
        return workflow.all();
    }

    @PostMapping("/workflows")
    @PreAuthorize("hasAnyAuthority('WORKFLOWS:CREATE','WORKFLOWS:UPDATE')")
    public Object addWorkflow(@RequestBody Map<String, String> body, Principal p) {
        return workflow.add(body, p.getName());
    }

    @PutMapping("/workflows/{id}")
    @PreAuthorize("hasAuthority('WORKFLOWS:UPDATE')")
    public Object editWorkflow(@PathVariable Long id, @RequestBody Map<String, String> body, Principal p) {
        return workflow.update(id, body, p.getName());
    }

    @DeleteMapping("/workflows/{id}")
    @PreAuthorize("hasAnyAuthority('WORKFLOWS:DELETE','WORKFLOWS:UPDATE')")
    public void deleteWorkflow(@PathVariable Long id, Principal p) {
        workflow.delete(id, p.getName());
    }

    @GetMapping("/reports/daily")
    @PreAuthorize("hasAuthority('REPORTS:VIEW')")
    public Object dailyReport(@RequestParam(required = false) String date) {
        return reports.daily(date == null || date.isBlank() ? null : LocalDate.parse(date));
    }

    @GetMapping("/reports/monthly")
    @PreAuthorize("hasAuthority('REPORTS:VIEW')")
    public Object monthlyReport(@RequestParam int year, @RequestParam int month) {
        return reports.monthly(year, month);
    }

    @GetMapping("/reports/daily.xlsx")
    @PreAuthorize("hasAuthority('REPORTS:VIEW')")
    public ResponseEntity<byte[]> dailyExcel(@RequestParam(required = false) String date) {
        LocalDate d = date == null || date.isBlank() ? LocalDate.now() : LocalDate.parse(date);
        return excelFile(excel.workbook(reports.daily(d)), "daily-" + d + ".xlsx");
    }

    @GetMapping("/reports/monthly.xlsx")
    @PreAuthorize("hasAuthority('REPORTS:VIEW')")
    public ResponseEntity<byte[]> monthlyExcel(@RequestParam int year, @RequestParam int month) {
        return excelFile(excel.workbook(reports.monthly(year, month)),
                "monthly-" + year + "-" + String.format("%02d", month) + ".xlsx");
    }

    private ResponseEntity<byte[]> excelFile(byte[] bytes, String filename) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }
}
