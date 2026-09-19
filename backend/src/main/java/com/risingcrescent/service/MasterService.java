package com.risingcrescent.service;

import com.risingcrescent.audit.AuditService;
import com.risingcrescent.domain.entity.*;
import com.risingcrescent.domain.enums.ProductKind;
import com.risingcrescent.domain.enums.ProductStatus;
import com.risingcrescent.domain.enums.UnitOfMeasure;
import com.risingcrescent.domain.enums.WorkflowEntity;
import com.risingcrescent.event.DomainEvent;
import com.risingcrescent.event.EventPublisher;
import com.risingcrescent.repo.*;
import com.risingcrescent.workflow.WorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.*;

@Service
@RequiredArgsConstructor
public class MasterService {
    private final CategoryRepository categories;
    private final ProductRepository products;
    private final WarehouseRepository warehouses;
    private final SupplierRepository suppliers;
    private final UserAccountRepository users;
    private final RoleRepository roles;
    private final ScreenRepository screens;
    private final AuditService audit;
    private final WorkflowService workflow;
    private final EventPublisher events;
    private final PasswordEncoder encoder;
    private final CatalogService catalog;
    private final FileStorage files;
    private final ProcurementService procurement;

    public List<Category> listCategories() {
        return categories.findAll();
    }

    @Transactional
    public Category saveCategory(Map<String, Object> body, String actor) {
        Category c = body.get("id") != null
                ? categories.findById(Long.valueOf(body.get("id").toString())).orElse(new Category())
                : new Category();
        c.setName(body.get("name").toString());
        c.setSlug(body.get("name").toString().toLowerCase().replace(' ', '-'));
        c.setDescription((String) body.get("description"));
        c.setImageHint((String) body.get("imageHint"));
        c.setActive(body.get("active") == null || Boolean.parseBoolean(body.get("active").toString()));
        categories.save(c);
        audit.record(actor, "CATEGORY_SAVE", "CATEGORY", String.valueOf(c.getId()), c.getName());
        return c;
    }

    public List<Map<String, Object>> listProducts() {
        return products.findAll().stream().map(catalog::card).toList();
    }

    @Transactional
    public Map<String, Object> saveProduct(Map<String, Object> body, String actor) {
        Product p = body.get("id") != null
                ? products.findById(Long.valueOf(body.get("id").toString())).orElse(new Product())
                : new Product();
        boolean create = p.getId() == null;
        p.setSku(body.get("sku").toString());
        p.setName(body.get("name").toString());
        p.setDescription((String) body.get("description"));
        p.setCategory(categories.findById(Long.valueOf(body.get("categoryId").toString())).orElseThrow());
        p.setKind(ProductKind.valueOf(body.get("kind").toString()));
        p.setUom(UnitOfMeasure.valueOf(body.getOrDefault("uom", "KG").toString()));
        p.setOrigin((String) body.get("origin"));
        p.setGrade((String) body.get("grade"));
        p.setSeason((String) body.get("season"));
        p.setVariety((String) body.get("variety"));
        p.setStorageHint((String) body.get("storageHint"));
        p.setImageHint((String) body.get("imageHint"));
        if (body.get("shelfLifeDays") != null) p.setShelfLifeDays(Integer.valueOf(body.get("shelfLifeDays").toString()));
        if (body.get("retailPrice") != null) p.setRetailPrice(new BigDecimal(body.get("retailPrice").toString()));
        if (body.get("wholesalePrice") != null) p.setWholesalePrice(new BigDecimal(body.get("wholesalePrice").toString()));
        if (body.get("minWholesaleQty") != null) p.setMinWholesaleQty(new BigDecimal(body.get("minWholesaleQty").toString()));
        if (p.getStatus() == null) p.setStatus(ProductStatus.DRAFT);
        products.save(p);
        if (create) {
            workflow.step(WorkflowEntity.PRODUCT, p.getSku(), null, "DRAFT", actor, "Created");
        }
        audit.record(actor, "PRODUCT_SAVE", "PRODUCT", p.getSku(), p.getName());
        Map<String, Object> card = catalog.card(p);
        if (create) {
            Map<String, Object> seed = procurement.seedOpeningStockForProduct(p, actor);
            card.putAll(seed);
        }
        return card;
    }

    @Transactional
    public Map<String, Object> saveProductImage(Long productId, MultipartFile file, String actor) {
        Product p = products.findById(productId).orElseThrow(() -> new IllegalArgumentException("Product not found"));
        String url = files.store("products", file);
        p.setImageUrl(url);
        products.save(p);
        audit.record(actor, "PRODUCT_IMAGE", "PRODUCT", p.getSku(), url);
        return catalog.card(p);
    }

    @Transactional
    public Map<String, Object> submitProduct(Long id, String actor) {
        Product p = products.findById(id).orElseThrow();
        p.setStatus(ProductStatus.PENDING_APPROVAL);
        products.save(p);
        workflow.step(WorkflowEntity.PRODUCT, p.getSku(), "DRAFT", "PENDING_APPROVAL", actor, "Submitted");
        events.publish(DomainEvent.of("rc.notifications", "PRODUCT_PENDING", p.getSku(), Map.of("name", p.getName())));
        return catalog.card(p);
    }

    @Transactional
    public Map<String, Object> publishProduct(Long id, String actor, boolean approve) {
        Product p = products.findById(id).orElseThrow();
        String from = p.getStatus().name();
        p.setStatus(approve ? ProductStatus.PUBLISHED : ProductStatus.DRAFT);
        products.save(p);
        workflow.step(WorkflowEntity.PRODUCT, p.getSku(), from, p.getStatus().name(), actor, approve ? "Published" : "Returned");
        audit.record(actor, approve ? "PRODUCT_PUBLISH" : "PRODUCT_REJECT", "PRODUCT", p.getSku(), "");
        return catalog.card(p);
    }

    public List<Warehouse> listWarehouses() {
        return warehouses.findAll();
    }

    @Transactional
    public Warehouse saveWarehouse(Map<String, Object> body, String actor) {
        Warehouse w = body.get("id") != null
                ? warehouses.findById(Long.valueOf(body.get("id").toString())).orElse(new Warehouse())
                : new Warehouse();
        w.setCode(body.get("code").toString());
        w.setName(body.get("name").toString());
        w.setCity((String) body.get("city"));
        w.setZoneType((String) body.get("zoneType"));
        w.setActive(true);
        warehouses.save(w);
        audit.record(actor, "WAREHOUSE_SAVE", "WAREHOUSE", w.getCode(), w.getName());
        return w;
    }

    public List<Map<String, Object>> listSuppliers() {
        return suppliers.findAll().stream().map(this::viewSupplier).toList();
    }

    @Transactional
    public Map<String, Object> saveSupplier(Map<String, Object> body, String actor) {
        Supplier s = body.get("id") != null
                ? suppliers.findById(Long.valueOf(body.get("id").toString())).orElse(new Supplier())
                : new Supplier();
        s.setName(body.get("name").toString());
        s.setContactName((String) body.get("contactName"));
        s.setEmail((String) body.get("email"));
        s.setPhone((String) body.get("phone"));
        s.setOriginCountry((String) body.get("originCountry"));
        s.setSpecialties((String) body.get("specialties"));
        s.setPreferred(Boolean.parseBoolean(String.valueOf(body.getOrDefault("preferred", false))));
        s.setActive(true);
        if (body.containsKey("portalUsername")) {
            String username = body.get("portalUsername") == null ? "" : body.get("portalUsername").toString().trim();
            if (username.isBlank()) {
                s.setPortalUser(null);
            } else {
                s.setPortalUser(users.findByUsername(username)
                        .orElseThrow(() -> new IllegalArgumentException("No login named " + username)));
            }
        }
        suppliers.save(s);
        audit.record(actor, "SUPPLIER_SAVE", "SUPPLIER", String.valueOf(s.getId()), s.getName());
        return viewSupplier(s);
    }

    private Map<String, Object> viewSupplier(Supplier s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId());
        m.put("name", s.getName());
        m.put("contactName", s.getContactName());
        m.put("email", s.getEmail());
        m.put("phone", s.getPhone());
        m.put("originCountry", s.getOriginCountry());
        m.put("specialties", s.getSpecialties());
        m.put("preferred", s.isPreferred());
        m.put("portalUsername", s.getPortalUser() == null ? "" : s.getPortalUser().getUsername());
        return m;
    }

    public List<Map<String, Object>> listRoleOptions() {
        return roles.findAll().stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", r.getCode());
            m.put("name", r.getName());
            return m;
        }).toList();
    }

    public List<Map<String, Object>> listUsers() {
        return users.findAll().stream().map(u -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", u.getId());
            m.put("username", u.getUsername());
            m.put("fullName", u.getFullName());
            m.put("email", u.getEmail());
            m.put("phone", u.getPhone());
            m.put("active", u.isActive());
            m.put("channel", u.getPreferredChannel());
            m.put("creditLimit", u.getCreditLimit());
            m.put("roles", u.getRoles().stream().map(Role::getCode).toList());
            return m;
        }).toList();
    }

    @Transactional
    public Map<String, Object> saveUser(Map<String, Object> body, String actor) {
        UserAccount u;
        if (body.get("id") != null) {
            u = users.findById(Long.valueOf(body.get("id").toString())).orElseThrow();
        } else {
            u = new UserAccount();
            u.setUsername(body.get("username").toString());
            u.setPasswordHash(encoder.encode(body.getOrDefault("password", "ChangeMe@123").toString()));
            u.setActive(true);
        }
        u.setFullName(body.get("fullName").toString());
        if (body.get("email") != null) u.setEmail(body.get("email").toString());
        if (body.get("phone") != null) u.setPhone(body.get("phone").toString());
        if (body.get("creditLimit") != null) u.setCreditLimit(new BigDecimal(body.get("creditLimit").toString()));
        if (body.get("password") != null && !body.get("password").toString().isBlank()) {
            u.setPasswordHash(encoder.encode(body.get("password").toString()));
        }
        if (body.get("active") != null) u.setActive(Boolean.parseBoolean(body.get("active").toString()));
        if (body.get("roles") instanceof List<?> roleCodes) {
            Set<Role> set = new HashSet<>();
            for (Object code : roleCodes) {
                roles.findByCode(code.toString()).ifPresent(set::add);
            }
            u.setRoles(set);
        }
        users.save(u);
        audit.record(actor, "USER_SAVE", "USER", u.getUsername(), "roles updated");
        return Map.of("id", u.getId(), "username", u.getUsername());
    }

    public List<Map<String, Object>> listRoles() {
        return roles.findAll().stream().map(r -> Map.of(
                "id", r.getId(),
                "code", r.getCode(),
                "name", r.getName(),
                "description", r.getDescription() == null ? "" : r.getDescription(),
                "permissions", r.getPermissions()
        )).toList();
    }

    @Transactional
    public Role saveRolePermissions(Long id, List<String> permissions, String actor) {
        Role r = roles.findById(id).orElseThrow();
        r.setPermissions(new HashSet<>(permissions));
        roles.save(r);
        audit.record(actor, "ROLE_PERMISSIONS", "ROLE", r.getCode(), String.join(",", permissions));
        return r;
    }

    public List<Screen> listScreens() {
        return screens.findAll();
    }
}
