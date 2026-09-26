package com.shiningcrescent.service;

import com.shiningcrescent.audit.AuditService;
import com.shiningcrescent.domain.entity.*;
import com.shiningcrescent.repo.*;
import com.shiningcrescent.workflow.WorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DeletionService {
    private final CategoryRepository categories;
    private final ProductRepository products;
    private final WarehouseRepository warehouses;
    private final SupplierRepository suppliers;
    private final UserAccountRepository users;
    private final CartRepository carts;
    private final SalesOrderRepository orders;
    private final PurchaseOrderRepository pos;
    private final InventoryLotRepository lots;
    private final QualityInspectionRepository inspections;
    private final AppNotificationRepository notifications;
    private final WorkflowService workflow;
    private final AuditService audit;

    @Transactional
    public void deleteCategory(Long id, String actor) {
        Category c = categories.findById(id).orElseThrow(() -> new IllegalArgumentException("Category not found"));
        if (!products.findByCategory_Id(id).isEmpty()) {
            throw new IllegalArgumentException("Move or delete the products in this category first.");
        }
        categories.delete(c);
        audit.record(actor, "CATEGORY_DELETE", "CATEGORY", String.valueOf(id), c.getName());
    }

    @Transactional
    public void deleteProduct(Long id, String actor) {
        Product p = products.findById(id).orElseThrow(() -> new IllegalArgumentException("Product not found"));
        boolean onOrder = orders.findAll().stream()
                .flatMap(o -> o.getLines().stream())
                .anyMatch(l -> l.getProduct() != null && id.equals(l.getProduct().getId()));
        if (onOrder) {
            throw new IllegalArgumentException("This product is on a sales order and cannot be deleted.");
        }
        for (Cart cart : carts.findAll()) {
            cart.getItems().removeIf(i -> i.getProduct() != null && id.equals(i.getProduct().getId()));
            carts.save(cart);
        }
        List<InventoryLot> productLots = lots.findByProduct_Id(id);
        for (InventoryLot lot : productLots) {
            inspections.deleteAll(inspections.findByLot_Id(lot.getId()));
        }
        lots.deleteAll(productLots);
        for (PurchaseOrder po : pos.findAll()) {
            boolean removed = po.getLines().removeIf(l -> l.getProduct() != null && id.equals(l.getProduct().getId()));
            if (removed) {
                if (po.getLines().isEmpty()) {
                    inspections.deleteAll(inspections.findByPurchaseOrder_Id(po.getId()));
                    pos.delete(po);
                } else {
                    pos.save(po);
                }
            }
        }
        products.delete(p);
        audit.record(actor, "PRODUCT_DELETE", "PRODUCT", p.getSku(), p.getName());
    }

    @Transactional
    public void deleteWarehouse(Long id, String actor) {
        Warehouse w = warehouses.findById(id).orElseThrow(() -> new IllegalArgumentException("Warehouse not found"));
        boolean usedLot = lots.findAll().stream().anyMatch(l -> l.getWarehouse() != null && id.equals(l.getWarehouse().getId()));
        boolean usedPo = pos.findAll().stream().anyMatch(po -> po.getWarehouse() != null && id.equals(po.getWarehouse().getId()));
        if (usedLot || usedPo) {
            throw new IllegalArgumentException("This warehouse still has lots or purchase orders.");
        }
        warehouses.delete(w);
        audit.record(actor, "WAREHOUSE_DELETE", "WAREHOUSE", w.getCode(), w.getName());
    }

    @Transactional
    public void deleteSupplier(Long id, String actor) {
        Supplier s = suppliers.findById(id).orElseThrow(() -> new IllegalArgumentException("Supplier not found"));
        boolean usedPo = pos.findAll().stream().anyMatch(po -> po.getSupplier() != null && id.equals(po.getSupplier().getId()));
        if (usedPo) {
            throw new IllegalArgumentException("This supplier still has purchase orders.");
        }
        lots.findAll().stream()
                .filter(l -> l.getSupplier() != null && id.equals(l.getSupplier().getId()))
                .forEach(l -> {
                    l.setSupplier(null);
                    lots.save(l);
                });
        suppliers.delete(s);
        audit.record(actor, "SUPPLIER_DELETE", "SUPPLIER", String.valueOf(id), s.getName());
    }

    @Transactional
    public void deleteUser(Long id, String actor) {
        UserAccount u = users.findById(id).orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (u.getUsername().equals(actor)) {
            throw new IllegalArgumentException("You cannot delete the account you are signed in with.");
        }
        if (!orders.findByCustomerOrderByPlacedAtDesc(u).isEmpty()) {
            throw new IllegalArgumentException("This user has sales orders and cannot be deleted.");
        }
        suppliers.findAll().stream()
                .filter(s -> s.getPortalUser() != null && id.equals(s.getPortalUser().getId()))
                .forEach(s -> {
                    s.setPortalUser(null);
                    suppliers.save(s);
                });
        carts.findByOwner(u).ifPresent(carts::delete);
        notifications.deleteAll(notifications.findByRecipientUsernameOrderByCreatedAtDesc(u.getUsername()));
        users.delete(u);
        audit.record(actor, "USER_DELETE", "USER", u.getUsername(), "Removed login");
    }

    @Transactional
    public void deletePurchaseOrder(Long id, String actor) {
        PurchaseOrder po = pos.findById(id).orElseThrow(() -> new IllegalArgumentException("Purchase order not found"));
        inspections.deleteAll(inspections.findByPurchaseOrder_Id(id));
        pos.delete(po);
        audit.record(actor, "PO_DELETE", "PURCHASE_ORDER", po.getPoNo(), "Deleted");
        workflow.step(com.shiningcrescent.domain.enums.WorkflowEntity.PURCHASE_ORDER, po.getPoNo(),
                po.getStatus() == null ? null : po.getStatus().name(), "DELETED", actor, "Deleted");
    }

    @Transactional
    public void deleteInspection(Long id, String actor) {
        QualityInspection qi = inspections.findById(id).orElseThrow(() -> new IllegalArgumentException("Inspection not found"));
        inspections.delete(qi);
        audit.record(actor, "QC_DELETE", "QUALITY", qi.getInspectionNo(), "Deleted");
    }

    @Transactional
    public void deleteLot(Long id, String actor) {
        InventoryLot lot = lots.findById(id).orElseThrow(() -> new IllegalArgumentException("Lot not found"));
        if (lot.getReservedQty() != null && lot.getReservedQty().signum() > 0) {
            throw new IllegalArgumentException("This lot still has reserved quantity.");
        }
        inspections.deleteAll(inspections.findByLot_Id(id));
        lots.delete(lot);
        audit.record(actor, "LOT_DELETE", "INVENTORY", lot.getLotCode(), "Deleted");
    }
}
