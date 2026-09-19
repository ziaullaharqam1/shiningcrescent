package com.risingcrescent.seed;

import com.risingcrescent.domain.entity.*;
import com.risingcrescent.domain.enums.*;
import com.risingcrescent.repo.*;
import com.risingcrescent.service.PortalService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {
    private final RoleRepository roles;
    private final UserAccountRepository users;
    private final ScreenRepository screens;
    private final CategoryRepository categories;
    private final ProductRepository products;
    private final WarehouseRepository warehouses;
    private final SupplierRepository suppliers;
    private final InventoryLotRepository lots;
    private final RiderRepository riders;
    private final PasswordEncoder encoder;
    private final PortalService portal;

    @Override
    @Transactional
    public void run(String... args) {
        if (users.count() == 0) {
            seedScreens();
            Map<String, Role> roleMap = seedRoles();
            seedUsers(roleMap);
            seedMasters();
        }
        if (riders.count() == 0) {
            seedRiders();
        }
        portal.ensureDefaults();
    }

    private void seedScreens() {
        record S(String code, String name, String module, String path, int sort) {}
        List<S> list = List.of(
                new S("STORE", "Marketplace", "Commerce", "/", 10),
                new S("CART", "Cart & checkout", "Commerce", "/cart", 20),
                new S("MY_ORDERS", "My orders", "Commerce", "/orders", 30),
                new S("DASHBOARD", "Trading desk", "Console", "/console", 40),
                new S("PRODUCTS", "Product master", "Masters", "/console/products", 50),
                new S("CATEGORIES", "Category master", "Masters", "/console/categories", 60),
                new S("SUPPLIERS", "Supplier master", "Masters", "/console/suppliers", 70),
                new S("WAREHOUSES", "Warehouse master", "Masters", "/console/warehouses", 80),
                new S("PURCHASE_ORDERS", "Purchase orders", "Procure", "/console/purchase-orders", 90),
                new S("QUALITY", "Quality inspection", "Quality", "/console/quality", 100),
                new S("INVENTORY", "Lot inventory", "Warehouse", "/console/inventory", 110),
                new S("SALES_ORDERS", "Sales orders", "Sales", "/console/sales-orders", 120),
                new S("FULFILLMENT", "Pick pack ship", "Warehouse", "/console/fulfillment", 130),
                new S("INVOICES", "Invoices & payments", "Finance", "/console/invoices", 140),
                new S("REPORTS", "Daily and monthly reports", "Console", "/console/reports", 145),
                new S("AGENT", "Agent chat", "Platform", "/console", 148),
                new S("USERS", "Users", "Admin", "/console/users", 150),
                new S("PORTAL", "Portal admin", "Admin", "/console/portal", 155),
                new S("OPS", "Operations", "Admin", "/console/ops", 156),
                new S("ROLES", "Roles & permissions", "Admin", "/console/roles", 160),
                new S("NOTIFICATIONS", "Notifications", "Platform", "/console/notifications", 170),
                new S("AUDIT", "Audit trail", "Platform", "/console/audit", 180),
                new S("WORKFLOWS", "Workflow history", "Platform", "/console/workflows", 190)
        );
        for (S s : list) {
            screens.save(Screen.builder().code(s.code).name(s.name).module(s.module).path(s.path).sortOrder(s.sort).build());
        }
    }

    private Set<String> allPerms() {
        Set<String> p = new LinkedHashSet<>();
        for (Screen s : screens.findAll()) {
            for (String a : List.of("VIEW", "CREATE", "UPDATE", "APPROVE", "DELETE")) {
                p.add(s.getCode() + ":" + a);
            }
        }
        return p;
    }

    private Set<String> grant(String... codes) {
        return new LinkedHashSet<>(Arrays.asList(codes));
    }

    private Set<String> screen(String code, String... actions) {
        Set<String> p = new LinkedHashSet<>();
        for (String a : actions) p.add(code + ":" + a);
        return p;
    }

    private Map<String, Role> seedRoles() {
        Map<String, Role> map = new LinkedHashMap<>();
        map.put("SUPER_ADMIN", role("SUPER_ADMIN", "Super administrator", "Full platform control", allPerms()));
        Set<String> mgr = new LinkedHashSet<>(allPerms());
        mgr.removeIf(x -> x.startsWith("ROLES:") && x.endsWith("DELETE"));
        mgr.removeIf(x -> x.startsWith("PORTAL:"));
        map.put("TRADING_MANAGER", role("TRADING_MANAGER", "Trading manager", "Approves catalog, POs and large orders", mgr));
        map.put("PROCUREMENT_OFFICER", role("PROCUREMENT_OFFICER", "Procurement officer", "Sourcing and purchase orders", union(
                screen("DASHBOARD", "VIEW"),
                screen("SUPPLIERS", "VIEW", "CREATE", "UPDATE"),
                screen("WAREHOUSES", "VIEW"),
                screen("PRODUCTS", "VIEW"),
                screen("PURCHASE_ORDERS", "VIEW", "CREATE", "UPDATE"),
                screen("INVENTORY", "VIEW", "CREATE", "UPDATE"),
                screen("QUALITY", "VIEW"),
                screen("NOTIFICATIONS", "VIEW"),
                screen("WORKFLOWS", "VIEW"),
                screen("REPORTS", "VIEW"),
                screen("AGENT", "VIEW", "CREATE")
        )));
        map.put("WAREHOUSE_KEEPER", role("WAREHOUSE_KEEPER", "Warehouse keeper", "Lots, GRN, FEFO pick-pack-ship", union(
                screen("DASHBOARD", "VIEW"),
                screen("WAREHOUSES", "VIEW"),
                screen("INVENTORY", "VIEW", "CREATE", "UPDATE"),
                screen("PURCHASE_ORDERS", "VIEW", "UPDATE"),
                screen("FULFILLMENT", "VIEW", "UPDATE"),
                screen("SALES_ORDERS", "VIEW"),
                screen("QUALITY", "VIEW"),
                screen("NOTIFICATIONS", "VIEW"),
                screen("REPORTS", "VIEW")
        )));
        map.put("QUALITY_INSPECTOR", role("QUALITY_INSPECTOR", "Quality inspector", "Lot moisture, grade, hold/release", union(
                screen("DASHBOARD", "VIEW"),
                screen("QUALITY", "VIEW", "UPDATE", "APPROVE"),
                screen("INVENTORY", "VIEW", "UPDATE"),
                screen("PURCHASE_ORDERS", "VIEW"),
                screen("NOTIFICATIONS", "VIEW"),
                screen("WORKFLOWS", "VIEW"),
                screen("REPORTS", "VIEW")
        )));
        map.put("SALES_EXECUTIVE", role("SALES_EXECUTIVE", "Wholesale sales", "B2B quotes and order confirmation", union(
                screen("DASHBOARD", "VIEW"),
                screen("STORE", "VIEW"),
                screen("CART", "VIEW", "CREATE", "UPDATE"),
                screen("MY_ORDERS", "VIEW", "CREATE"),
                screen("SALES_ORDERS", "VIEW", "CREATE", "UPDATE", "APPROVE"),
                screen("PRODUCTS", "VIEW"),
                screen("INVENTORY", "VIEW"),
                screen("INVOICES", "VIEW"),
                screen("USERS", "VIEW"),
                screen("NOTIFICATIONS", "VIEW"),
                screen("REPORTS", "VIEW"),
                screen("AGENT", "VIEW", "CREATE")
        )));
        map.put("SALES_MANAGER", role("SALES_MANAGER", "Sales manager", "Wholesale desk, credit hold, invoice follow-up", union(
                screen("DASHBOARD", "VIEW"),
                screen("STORE", "VIEW"),
                screen("CART", "VIEW", "CREATE", "UPDATE"),
                screen("MY_ORDERS", "VIEW", "CREATE"),
                screen("SALES_ORDERS", "VIEW", "CREATE", "UPDATE", "APPROVE"),
                screen("PRODUCTS", "VIEW"),
                screen("INVENTORY", "VIEW"),
                screen("INVOICES", "VIEW", "UPDATE"),
                screen("USERS", "VIEW"),
                screen("NOTIFICATIONS", "VIEW"),
                screen("WORKFLOWS", "VIEW"),
                screen("REPORTS", "VIEW"),
                screen("AGENT", "VIEW", "CREATE")
        )));
        map.put("CATALOG_MERCHANDISER", role("CATALOG_MERCHANDISER", "Catalog merchandiser", "SKU, origin, grade listings", union(
                screen("DASHBOARD", "VIEW"),
                screen("PRODUCTS", "VIEW", "CREATE", "UPDATE"),
                screen("CATEGORIES", "VIEW", "CREATE", "UPDATE"),
                screen("NOTIFICATIONS", "VIEW"),
                screen("AGENT", "VIEW", "CREATE")
        )));
        map.put("FINANCE_OFFICER", role("FINANCE_OFFICER", "Finance officer", "Credit, invoices, collections", union(
                screen("DASHBOARD", "VIEW"),
                screen("INVOICES", "VIEW", "CREATE", "UPDATE", "APPROVE"),
                screen("SALES_ORDERS", "VIEW", "APPROVE"),
                screen("USERS", "VIEW", "UPDATE"),
                screen("AUDIT", "VIEW"),
                screen("NOTIFICATIONS", "VIEW"),
                screen("REPORTS", "VIEW")
        )));
        map.put("CUSTOMER", role("CUSTOMER", "Retail / trade buyer", "Shop, cart, own orders", union(
                screen("STORE", "VIEW"),
                screen("CART", "VIEW", "CREATE", "UPDATE"),
                screen("MY_ORDERS", "VIEW", "CREATE"),
                screen("NOTIFICATIONS", "VIEW"),
                screen("AGENT", "VIEW", "CREATE")
        )));
        map.put("SUPPLIER", role("SUPPLIER", "Grower / packer portal", "Assigned POs, lots and QC for your goods", union(
                screen("DASHBOARD", "VIEW"),
                screen("PURCHASE_ORDERS", "VIEW", "UPDATE"),
                screen("PRODUCTS", "VIEW"),
                screen("QUALITY", "VIEW"),
                screen("INVENTORY", "VIEW"),
                screen("NOTIFICATIONS", "VIEW"),
                screen("WORKFLOWS", "VIEW"),
                screen("AGENT", "VIEW", "CREATE")
        )));
        return map;
    }

    @SafeVarargs
    private final Set<String> union(Set<String>... sets) {
        Set<String> all = new LinkedHashSet<>();
        for (Set<String> s : sets) all.addAll(s);
        return all;
    }

    private Role role(String code, String name, String desc, Set<String> perms) {
        return roles.save(Role.builder().code(code).name(name).description(desc).permissions(perms).build());
    }

    private void seedUsers(Map<String, Role> r) {
        user("admin", "Admin@123", "Amina Crescent", "admin@risingcrescent.com", Channel.WHOLESALE, r.get("SUPER_ADMIN"));
        user("manager", "Mgr@123", "Ravi Trading Desk", "manager@risingcrescent.com", Channel.WHOLESALE, r.get("TRADING_MANAGER"));
        user("procure", "Proc@123", "Laila Sourcing", "procure@risingcrescent.com", Channel.WHOLESALE, r.get("PROCUREMENT_OFFICER"));
        user("warehouse", "Ware@123", "Omar Cold Store", "warehouse@risingcrescent.com", Channel.WHOLESALE, r.get("WAREHOUSE_KEEPER"));
        user("qa", "Qa@123", "Noor Quality", "qa@risingcrescent.com", Channel.WHOLESALE, r.get("QUALITY_INSPECTOR"));
        user("sales", "Sales@123", "Hana Wholesale", "sales@risingcrescent.com", Channel.WHOLESALE, r.get("SALES_EXECUTIVE"));
        user("salesmgr", "SalesMgr@123", "Nour Sales Desk", "salesmgr@risingcrescent.com", Channel.WHOLESALE, r.get("SALES_MANAGER"));
        user("merch", "Merch@123", "Yusuf Catalog", "merch@risingcrescent.com", Channel.RETAIL, r.get("CATALOG_MERCHANDISER"));
        user("finance", "Fin@123", "Sara Ledger", "finance@risingcrescent.com", Channel.WHOLESALE, r.get("FINANCE_OFFICER"));
        UserAccount cust = user("buyer", "Buyer@123", "Maya Orchard Club", "maya@example.com", Channel.RETAIL, r.get("CUSTOMER"));
        cust.setCity("Dubai");
        cust.setCreditLimit(new BigDecimal("5000"));
        users.save(cust);
        UserAccount trade = user("wholesale", "Trade@123", "Gulf Grocer LLC", "trade@example.com", Channel.WHOLESALE, r.get("CUSTOMER"));
        trade.setCompanyName("Gulf Grocer LLC");
        trade.setCreditLimit(new BigDecimal("25000"));
        users.save(trade);
        UserAccount suppUser = user("supplier", "Supp@123", "Anatolia Packers", "supplier@example.com", Channel.WHOLESALE, r.get("SUPPLIER"));
        users.save(suppUser);
        user("kashmir", "Kashmir@123", "Kashmir Orchard Co", "farah@kashmir.test", Channel.WHOLESALE, r.get("SUPPLIER"));
        user("dates", "Dates@123", "Al Batinah Dates", "salim@dates.test", Channel.WHOLESALE, r.get("SUPPLIER"));
    }

    private UserAccount user(String username, String password, String name, String email, Channel channel, Role role) {
        return users.save(UserAccount.builder()
                .username(username)
                .passwordHash(encoder.encode(password))
                .fullName(name)
                .email(email)
                .active(true)
                .preferredChannel(channel)
                .roles(new HashSet<>(Set.of(role)))
                .creditLimit(BigDecimal.ZERO)
                .outstandingCredit(BigDecimal.ZERO)
                .build());
    }

    private void seedMasters() {
        Category fresh = cat("Fresh fruit", "Seasonal orchard fruit, cold-chain packed");
        Category dry = cat("Dry fruit", "Sun and tunnel dried lots with moisture control");
        Category nuts = cat("Nuts", "Tree nuts, roasted and raw grades");
        Category mix = cat("Trail & gift mixes", "Blended retail packs and corporate hampers");

        Warehouse cold = warehouses.save(Warehouse.builder().code("DXB-COLD").name("Dubai cold chain").city("Dubai").zoneType("Chilled 2-8C").active(true).build());
        Warehouse ambient = warehouses.save(Warehouse.builder().code("DXB-DRY").name("Dubai dry store").city("Dubai").zoneType("Ambient humidity-controlled").active(true).build());

        Supplier anatolia = suppliers.save(Supplier.builder().name("Anatolia Packers").contactName("Emre").email("emre@anatolia.test")
                .originCountry("Turkey").specialties("Dried apricot, fig, raisin").preferred(true).active(true)
                .portalUser(users.findByUsername("supplier").orElse(null)).build());
        Supplier kashmir = suppliers.save(Supplier.builder().name("Kashmir Orchard Co").contactName("Farah").email("farah@kashmir.test")
                .originCountry("India").specialties("Apple, walnut, almond").preferred(true).active(true)
                .portalUser(users.findByUsername("kashmir").orElse(null)).build());
        Supplier oman = suppliers.save(Supplier.builder().name("Al Batinah Dates").contactName("Salim").email("salim@dates.test")
                .originCountry("Oman").specialties("Fard, Khalas dates").preferred(true).active(true)
                .portalUser(users.findByUsername("dates").orElse(null)).build());

        Product p1 = prod("FF-APL-01", "Kashmir Royal apple", fresh, ProductKind.FRESH_FRUIT, "India", "Premium",
                "Crisp high-altitude apples. Packed in 10kg trays for retail and foodservice.", "apple",
                new BigDecimal("12.50"), new BigDecimal("9.80"), 14, true);
        Product p2 = prod("FF-MNG-01", "Alphonso mango", fresh, ProductKind.FRESH_FRUIT, "India", "A",
                "Short-season Alphonso. Air-freight lots, eat within the week.", "mango",
                new BigDecimal("28.00"), new BigDecimal("22.00"), 7, true);
        Product p3 = prod("FF-CIT-01", "Jaffa orange", fresh, ProductKind.FRESH_FRUIT, "Egypt", "A",
                "Juicy citrus for juice bars and home crates.", "orange",
                new BigDecimal("6.40"), new BigDecimal("4.90"), 21, true);
        Product p4 = prod("DF-DAT-01", "Fard dates", dry, ProductKind.DRY_FRUIT, "Oman", "Premium",
                "Soft Fard dates, vacuum packed. Ideal for retail pouches and hampers.", "dates",
                new BigDecimal("18.00"), new BigDecimal("14.20"), 365, false);
        Product p5 = prod("DF-APR-01", "Malatya dried apricot", dry, ProductKind.DRY_FRUIT, "Turkey", "No.1",
                "Sulphured No.1 apricots. Moisture targeted 20-25%.", "apricot",
                new BigDecimal("22.50"), new BigDecimal("17.80"), 540, false);
        Product p6 = prod("DF-FIG-01", "Aydin dried fig", dry, ProductKind.DRY_FRUIT, "Turkey", "Lerida",
                "Natural Lerida figs, size 1-3, low foreign matter.", "fig",
                new BigDecimal("24.00"), new BigDecimal("19.40"), 540, false);
        Product p7 = prod("DF-RAI-01", "Golden raisins", dry, ProductKind.DRY_FRUIT, "Iran", "Jumbo",
                "Golden jumbo raisins for bakery and retail.", "raisin",
                new BigDecimal("11.20"), new BigDecimal("8.60"), 540, false);
        Product p8 = prod("NT-ALM-01", "Mamra almonds", nuts, ProductKind.NUT, "India", "Premium",
                "Oil-rich Mamra almonds, hand sorted.", "almond",
                new BigDecimal("42.00"), new BigDecimal("36.50"), 365, false);
        Product p9 = prod("NT-CSH-01", "W320 cashews", nuts, ProductKind.NUT, "Vietnam", "W320",
                "White whole cashews for roasting and gifting.", "cashew",
                new BigDecimal("31.00"), new BigDecimal("26.80"), 365, false);
        Product p10 = prod("NT-PST-01", "Akbari pistachios", nuts, ProductKind.NUT, "Iran", "Akbari",
                "Long Akbari pistachios, naturally open.", "pistachio",
                new BigDecimal("38.00"), new BigDecimal("32.00"), 365, false);
        Product p11 = prod("NT-WNT-01", "Kashmir walnuts", nuts, ProductKind.NUT, "India", "Light halves",
                "Light walnut halves, low kernel stain.", "walnut",
                new BigDecimal("27.00"), new BigDecimal("22.40"), 270, false);
        Product p12 = prod("MX-TRL-01", "Crescent trail mix", mix, ProductKind.MIX, "UAE blend", "House",
                "Almond, raisin, cashew and pumpkin seed mix for retail pouches.", "mix",
                new BigDecimal("16.80"), new BigDecimal("13.20"), 180, false);

        lot("LOT-APL-2401", p1, cold, kashmir, new BigDecimal("240"), LocalDate.now().plusDays(10));
        lot("LOT-MNG-2401", p2, cold, kashmir, new BigDecimal("80"), LocalDate.now().plusDays(5));
        lot("LOT-CIT-2401", p3, cold, oman, new BigDecimal("400"), LocalDate.now().plusDays(18));
        lot("LOT-DAT-2401", p4, ambient, oman, new BigDecimal("500"), LocalDate.now().plusMonths(8));
        lot("LOT-APR-2401", p5, ambient, anatolia, new BigDecimal("300"), LocalDate.now().plusMonths(12));
        lot("LOT-FIG-2401", p6, ambient, anatolia, new BigDecimal("180"), LocalDate.now().plusMonths(10));
        lot("LOT-RAI-2401", p7, ambient, anatolia, new BigDecimal("350"), LocalDate.now().plusMonths(11));
        lot("LOT-ALM-2401", p8, ambient, kashmir, new BigDecimal("120"), LocalDate.now().plusMonths(9));
        lot("LOT-CSH-2401", p9, ambient, anatolia, new BigDecimal("200"), LocalDate.now().plusMonths(8));
        lot("LOT-PST-2401", p10, ambient, anatolia, new BigDecimal("90"), LocalDate.now().plusMonths(7));
        lot("LOT-WNT-2401", p11, ambient, kashmir, new BigDecimal("150"), LocalDate.now().plusMonths(6));
        lot("LOT-MIX-2401", p12, ambient, anatolia, new BigDecimal("220"), LocalDate.now().plusMonths(4));
    }

    private Category cat(String name, String desc) {
        return categories.save(Category.builder().name(name).slug(name.toLowerCase().replace(' ', '-'))
                .description(desc).active(true).build());
    }

    private Product prod(String sku, String name, Category cat, ProductKind kind, String origin, String grade,
                         String desc, String image, BigDecimal retail, BigDecimal wholesale, int life, boolean perishable) {
        return products.save(Product.builder()
                .sku(sku).name(name).category(cat).kind(kind).uom(UnitOfMeasure.KG)
                .origin(origin).grade(grade).description(desc).imageHint(image)
                .retailPrice(retail).wholesalePrice(wholesale).minWholesaleQty(new BigDecimal("10"))
                .shelfLifeDays(life).perishable(perishable).status(ProductStatus.PUBLISHED)
                .storageHint(perishable ? "Keep chilled, FEFO pick" : "Humidity-controlled ambient")
                .moistureMaxPct(perishable ? null : new BigDecimal("25"))
                .build());
    }

    private void lot(String code, Product p, Warehouse w, Supplier s, BigDecimal qty, LocalDate expiry) {
        lots.save(InventoryLot.builder()
                .lotCode(code).product(p).warehouse(w).supplier(s)
                .receivedQty(qty).availableQty(qty).reservedQty(BigDecimal.ZERO)
                .receivedOn(LocalDate.now().minusDays(3)).expiryOn(expiry)
                .grade(p.getGrade()).status(LotStatus.AVAILABLE).moisturePct(p.getMoistureMaxPct())
                .build());
    }

    private void seedRiders() {
        rider("Hassan Al Nuaimi", "0501110001", "Noon bike", "NR-DXB-104", "4.92", 412, 25.1181, 55.2010, true);
        rider("Priya Menon", "0501110002", "Noon bike", "NR-DXB-218", "4.87", 288, 25.1250, 55.2102, true);
        rider("Youssef Farid", "0501110003", "Noon car", "NR-DXB-331", "4.95", 640, 25.1102, 55.1899, true);
        rider("Lina Kostas", "0501110007", "Noon car", "NR-DXB-719", "4.83", 210, 25.1088, 55.2144, true);
        rider("Aisha Rahman", "0501110004", "Noon bike", "NR-DXB-447", "4.81", 156, 25.1322, 55.2215, true);
        rider("Omar Haddad", "0501110005", "Noon bike", "NR-DXB-512", "4.76", 91, 25.1020, 55.1955, true);
        rider("Mei Chen", "0501110006", "Noon bike", "NR-DXB-608", "4.88", 203, 25.1408, 55.1988, false);
    }

    private void rider(String name, String phone, String vehicle, String code, String rating, int jobs, double lat, double lng, boolean available) {
        riders.save(Rider.builder()
                .name(name).phone(phone).vehicle(vehicle).noonRiderCode(code)
                .rating(new BigDecimal(rating)).jobsCompleted(jobs)
                .available(available).onJob(!available)
                .lastLat(lat).lastLng(lng)
                .build());
    }
}
