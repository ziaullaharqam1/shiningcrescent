package com.shiningcrescent.agent;

import com.shiningcrescent.config.AgentProperties;
import com.shiningcrescent.domain.entity.Category;
import com.shiningcrescent.domain.entity.Product;
import com.shiningcrescent.domain.entity.PurchaseOrder;
import com.shiningcrescent.domain.entity.UserAccount;
import com.shiningcrescent.repo.CategoryRepository;
import com.shiningcrescent.repo.ProductRepository;
import com.shiningcrescent.repo.SupplierRepository;
import com.shiningcrescent.repo.UserAccountRepository;
import com.shiningcrescent.repo.WarehouseRepository;
import com.shiningcrescent.security.RoleChecks;
import com.shiningcrescent.service.CartService;
import com.shiningcrescent.service.DeletionService;
import com.shiningcrescent.service.MasterService;
import com.shiningcrescent.service.OrderService;
import com.shiningcrescent.service.ProcurementService;
import com.shiningcrescent.service.ReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentWorkflowService {
    private static final Pattern PO_REF = Pattern.compile("(PO-\\d+)", Pattern.CASE_INSENSITIVE);

    private final MasterService masters;
    private final ProcurementService procurement;
    private final CartService carts;
    private final OrderService orders;
    private final UserAccountRepository users;
    private final ReportService reports;
    private final DeletionService deletions;
    private final ProductRepository products;
    private final CategoryRepository categories;
    private final SupplierRepository suppliers;
    private final WarehouseRepository warehouses;
    private final AgentProperties props;

    public Map<String, Object> chat(String username, String message, List<Map<String, String>> history) {
        String text = message == null ? "" : message.trim();
        if (text.isBlank() || isHelp(text)) {
            return reply(help(), List.of());
        }
        List<Map<String, Object>> planned = plan(text, history);
        if (planned.isEmpty()) {
            return reply("I could not match that. Use one of these formats:\n\n" + help(), List.of());
        }
        List<Map<String, Object>> results = new ArrayList<>();
        StringBuilder spoken = new StringBuilder();
        for (Map<String, Object> step : planned) {
            Map<String, Object> ran = run(username, normalizeStep(step));
            results.add(ran);
            if (ran.get("message") != null) {
                if (!spoken.isEmpty()) spoken.append("\n");
                spoken.append(ran.get("message"));
            }
        }
        return reply(spoken.toString(), results);
    }

    private List<Map<String, Object>> plan(String text, List<Map<String, String>> history) {
        List<Map<String, Object>> local = parse(text);
        if (!local.isEmpty() || props.getOpenaiKey() == null || props.getOpenaiKey().isBlank()) {
            return local;
        }
        try {
            return llmPlan(text, history);
        } catch (Exception e) {
            log.warn("LLM planner unavailable: {}", e.getMessage());
            return local;
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> llmPlan(String text, List<Map<String, String>> history) throws Exception {
        RestClient client = RestClient.builder().baseUrl(props.getOpenaiBaseUrl()).build();
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", """
                You plan tools for Shining Crescent trading. Return JSON only: {"tools":[{"name":"...","args":{}}]}
                Tools: search_items, create_product, edit_product, delete_product, create_purchase_order, edit_purchase_order,
                approve_po_or_quarantine, generate_daily_report, generate_monthly_report, add_to_cart, create_sales_order.
                search_items args: query (name, sku, or fragment)
                create_product args: sku, name, category (or categoryId), kind, retailPrice, wholesalePrice, origin, grade
                edit_product args: sku or id, plus fields to change
                delete_product args: sku or id or product name
                create_purchase_order args: product (name/sku), qty, supplier (optional), warehouse (optional), unitCost
                edit_purchase_order args: poNo, qty, notes, expectedDate
                approve_po_or_quarantine args: poNo, action = APPROVE or QUARANTINE
                generate_daily_report args: date (YYYY-MM-DD optional)
                generate_monthly_report args: year, month
                add_to_cart args: product (name/sku), qty
                create_sales_order args: product (name/sku, optional if cart already has items), qty, address (optional)
                "create order" and "place order" mean create_sales_order (customer sales), NOT a purchase order.
                "create PO" / "purchase order" means create_purchase_order.
                """));
        if (history != null) {
            for (Map<String, String> h : history) {
                messages.add(Map.of("role", h.getOrDefault("role", "user"), "content", h.getOrDefault("content", "")));
            }
        }
        messages.add(Map.of("role", "user", "content", text));
        Map<String, Object> body = Map.of(
                "model", props.getOpenaiModel(),
                "temperature", 0,
                "response_format", Map.of("type", "json_object"),
                "messages", messages
        );
        Map<?, ?> resp = client.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + props.getOpenaiKey())
                .body(body)
                .retrieve()
                .body(Map.class);
        List<?> choices = (List<?>) resp.get("choices");
        Map<?, ?> msg = (Map<?, ?>) ((Map<?, ?>) choices.get(0)).get("message");
        String content = String.valueOf(msg.get("content"));
        com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
        Map<String, Object> parsed = om.readValue(content, Map.class);
        Object tools = parsed.get("tools");
        if (tools instanceof List<?> list) {
            return list.stream()
                    .filter(item -> item instanceof Map<?, ?>)
                    .map(item -> normalizeStep((Map<String, Object>) item))
                    .filter(step -> !str(step.get("name")).isBlank())
                    .toList();
        }
        return List.of();
    }

    private List<Map<String, Object>> parse(String raw) {
        String text = raw.toLowerCase(Locale.ROOT);
        List<Map<String, Object>> steps = new ArrayList<>();
        boolean deletingProduct = (text.contains("delete") || text.contains("remove")) && text.contains("product");
        boolean searching = text.contains("search") || text.contains("look up") || text.contains("lookup")
                || ((text.contains("find") || text.startsWith("show "))
                && (text.contains("item") || text.contains("product") || text.contains("sku")));
        if (deletingProduct) {
            Map<String, Object> args = new LinkedHashMap<>();
            args.put("sku", extractSku(raw));
            args.put("product", firstNonBlank(extractQuoted(raw), extractAfter(raw, "product"), extractProductHint(raw)));
            steps.add(tool("delete_product", args));
            return steps;
        }
        if (searching) {
            steps.add(tool("search_items", Map.of("query", extractSearchQuery(raw))));
            return steps;
        }
        boolean purchasePo = text.contains("purchase order") || text.contains("purchase-order")
                || text.contains(" create po") || text.startsWith("create po") || text.startsWith("raise po")
                || text.contains("raise po") || text.contains(" po-") || text.matches(".*\\bpo-\\d+.*");
        boolean cartIntent = (text.contains("cart") || text.contains("basket"))
                && (text.contains("add") || text.contains("put") || text.contains("buy")
                || text.contains("to cart") || text.contains("to the cart") || text.contains("in cart")
                || text.contains("into the cart") || text.contains("to basket"));
        boolean salesOrderIntent = !purchasePo && !cartIntent && (
                text.contains("create order") || text.contains("create an order") || text.contains("create sales order")
                        || text.contains("place order") || text.contains("place an order") || text.contains("place a order")
                        || text.contains("sales order") || text.contains("checkout") || text.contains("check out")
                        || text.contains("order me") || text.matches("(?s).*\\b(buy|order)\\s+\\d+.*")
                        || ((text.contains("create") || text.contains("place") || text.contains("make"))
                        && text.contains("order")));
        if (cartIntent) {
            steps.add(tool("add_to_cart", Map.of(
                    "product", extractTradeProductHint(raw),
                    "qty", extractQty(raw, "1")
            )));
            return steps;
        }
        if (salesOrderIntent) {
            Map<String, Object> args = new LinkedHashMap<>();
            args.put("product", extractTradeProductHint(raw));
            args.put("qty", extractQty(raw, "1"));
            args.put("address", extractAddress(raw));
            args.put("name", extractAfter(raw, "name"));
            args.put("phone", extractAfter(raw, "phone"));
            steps.add(tool("create_sales_order", args));
            return steps;
        }
        if (text.contains("daily report") || text.contains("today's report") || text.contains("todays report")
                || (text.contains("generate") && text.contains("daily"))) {
            steps.add(tool("generate_daily_report", Map.of("date", extractDate(raw))));
        }
        if (text.contains("monthly report") || (text.contains("generate") && text.contains("month"))) {
            YearMonth ym = extractYearMonth(raw);
            steps.add(tool("generate_monthly_report", Map.of("year", ym.getYear(), "month", ym.getMonthValue())));
        }
        if (text.contains("quarantine")) {
            steps.add(tool("approve_po_or_quarantine", Map.of("poNo", extractPo(raw), "action", "QUARANTINE")));
        } else if (text.contains("approve po") || text.contains("approve the po") || text.contains("approve purchase")) {
            steps.add(tool("approve_po_or_quarantine", Map.of("poNo", extractPo(raw), "action", "APPROVE")));
        }
        if ((text.contains("edit") || text.contains("update") || text.contains("change")) && (text.contains("purchase") || text.contains(" po"))) {
            Map<String, Object> args = new LinkedHashMap<>();
            args.put("poNo", extractPo(raw));
            args.put("qty", extractQty(raw, ""));
            args.put("notes", extractAfter(raw, "notes"));
            steps.add(tool("edit_purchase_order", args));
        } else if ((text.contains("create") || text.contains("raise") || text.contains("new"))
                && (text.contains("purchase order") || text.contains(" po") || text.startsWith("create po"))) {
            steps.add(tool("create_purchase_order", Map.of(
                    "product", firstNonBlank(extractProductHint(raw), extractTradeProductHint(raw)),
                    "qty", extractQty(raw, "3"),
                    "supplier", extractAfter(raw, "supplier"),
                    "unitCost", extractNumberAfter(raw, "cost", "10")
            )));
        }
        if ((text.contains("edit") || text.contains("update") || text.contains("change")) && text.contains("product")) {
            Map<String, Object> args = new LinkedHashMap<>();
            args.put("sku", extractSku(raw));
            args.put("name", extractAfter(raw, "name"));
            putIfNumber(args, "retailPrice", extractNumberAfter(raw, "retail", ""));
            putIfNumber(args, "wholesalePrice", extractNumberAfter(raw, "wholesale", ""));
            steps.add(tool("edit_product", args));
        } else if ((text.contains("create") || text.contains("add") || text.contains("new")) && text.contains("product")) {
            Map<String, Object> args = new LinkedHashMap<>();
            args.put("sku", extractSku(raw));
            args.put("name", firstNonBlank(extractQuoted(raw), extractAfter(raw, "name"), extractProductHint(raw)));
            args.put("category", firstNonBlank(extractAfter(raw, "category"), "Dates"));
            args.put("kind", extractKind(raw));
            args.put("retailPrice", extractNumberAfter(raw, "retail", "20"));
            args.put("wholesalePrice", extractNumberAfter(raw, "wholesale", "16"));
            args.put("origin", firstNonBlank(extractAfter(raw, "origin"), "UAE"));
            args.put("grade", firstNonBlank(extractAfter(raw, "grade"), "A"));
            steps.add(tool("create_product", args));
        }
        return steps;
    }

    private Map<String, Object> run(String username, Map<String, Object> step) {
        String name = String.valueOf(step.getOrDefault("name", ""));
        @SuppressWarnings("unchecked")
        Map<String, Object> args = step.get("args") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
        try {
            return switch (name) {
                case "create_product" -> createProduct(username, args);
                case "edit_product" -> editProduct(username, args);
                case "delete_product" -> deleteProduct(username, args);
                case "search_items" -> searchItems(args);
                case "create_purchase_order" -> createPo(username, args);
                case "edit_purchase_order" -> editPo(username, args);
                case "approve_po_or_quarantine" -> approveOrQuarantine(username, args);
                case "generate_daily_report" -> daily(args);
                case "generate_monthly_report" -> monthly(args);
                case "add_to_cart" -> addCart(username, args);
                case "create_sales_order", "place_order", "checkout", "create_order" -> createSalesOrder(username, args);
                default -> Map.of("ok", false, "message", "I do not know that action.");
            };
        } catch (AccessDeniedException e) {
            return Map.of("ok", false, "tool", name, "message", "You do not have access to do that.");
        } catch (IllegalArgumentException e) {
            return Map.of("ok", false, "tool", name, "message", e.getMessage());
        } catch (Exception e) {
            log.warn("Agent tool {} failed", name, e);
            return Map.of("ok", false, "tool", name, "message", "I could not complete that. Check the details and try again.");
        }
    }

    private Map<String, Object> createProduct(String actor, Map<String, Object> args) {
        require("PRODUCTS:CREATE");
        Category cat = resolveCategory(str(args.get("category"), args.get("categoryId")));
        String sku = str(args.get("sku"));
        if (sku.isBlank()) {
            sku = "RC-" + System.currentTimeMillis() % 100000;
        }
        String name = str(args.get("name"));
        if (name.isBlank()) {
            throw new IllegalArgumentException("Give the product a name.");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sku", sku);
        body.put("name", name);
        body.put("categoryId", cat.getId());
        body.put("kind", extractKind(str(args.get("kind"))));
        body.put("uom", "KG");
        body.put("origin", firstNonBlank(str(args.get("origin")), "UAE"));
        body.put("grade", firstNonBlank(str(args.get("grade")), "A"));
        body.put("retailPrice", num(args.get("retailPrice"), "20"));
        body.put("wholesalePrice", num(args.get("wholesalePrice"), "16"));
        Map<String, Object> saved = masters.saveProduct(body, actor);
        String msg = "Created " + saved.get("name") + " (" + saved.get("sku") + ").";
        if (Boolean.TRUE.equals(saved.get("seeded"))) {
            msg += " Opening PO " + saved.get("starterPo") + " for 3 kg from " + saved.get("supplier")
                    + " was auto-approved for QA. Lot " + saved.get("lotCode") + " is "
                    + saved.get("lotStatus") + " (" + saved.get("availableQty") + " available).";
        }
        return Map.of("ok", true, "tool", "create_product", "data", saved, "message", msg);
    }

    private Map<String, Object> editProduct(String actor, Map<String, Object> args) {
        require("PRODUCTS:UPDATE");
        Product p = resolveProduct(str(args.get("sku"), args.get("id"), args.get("product")));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", p.getId());
        body.put("sku", firstNonBlank(str(args.get("sku")), p.getSku()));
        body.put("name", firstNonBlank(str(args.get("name")), p.getName()));
        body.put("categoryId", p.getCategory().getId());
        body.put("kind", p.getKind() == null ? "DRY_FRUIT" : p.getKind().name());
        body.put("uom", p.getUom() == null ? "KG" : p.getUom().name());
        body.put("origin", firstNonBlank(str(args.get("origin")), p.getOrigin()));
        body.put("grade", firstNonBlank(str(args.get("grade")), p.getGrade()));
        body.put("retailPrice", args.get("retailPrice") != null ? args.get("retailPrice") : p.getRetailPrice());
        body.put("wholesalePrice", args.get("wholesalePrice") != null ? args.get("wholesalePrice") : p.getWholesalePrice());
        Map<String, Object> saved = masters.saveProduct(body, actor);
        return Map.of("ok", true, "tool", "edit_product", "data", saved,
                "message", "Updated product " + saved.get("sku") + ".");
    }

    private Map<String, Object> deleteProduct(String actor, Map<String, Object> args) {
        requireAny("PRODUCTS:UPDATE", "PRODUCTS:DELETE");
        Product p = resolveProduct(str(args.get("sku"), args.get("id"), args.get("product"), args.get("name")));
        String sku = p.getSku();
        String name = p.getName();
        deletions.deleteProduct(p.getId(), actor);
        return Map.of("ok", true, "tool", "delete_product",
                "message", "Deleted product " + sku + " (" + name + ").");
    }

    private Map<String, Object> searchItems(Map<String, Object> args) {
        requireAny("PRODUCTS:VIEW", "INVENTORY:VIEW");
        String q = str(args.get("query"), args.get("q"), args.get("product"), args.get("sku"));
        if (q.isBlank()) {
            throw new IllegalArgumentException("What should I search for?");
        }
        String n = q.toLowerCase(Locale.ROOT);
        List<Map<String, Object>> hits = new ArrayList<>();
        for (Map<String, Object> card : masters.listProducts()) {
            String blob = (String.valueOf(card.get("sku")) + " " + card.get("name") + " " + card.get("kind")
                    + " " + card.get("status") + " " + card.get("origin") + " " + card.get("grade")).toLowerCase(Locale.ROOT);
            if (blob.contains(n)) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("sku", card.get("sku"));
                row.put("name", card.get("name"));
                row.put("status", card.get("status"));
                row.put("availableQty", card.get("availableQty"));
                row.put("retailPrice", card.get("retailPrice"));
                hits.add(row);
            }
        }
        if (hits.isEmpty()) {
            return Map.of("ok", true, "tool", "search_items", "data", hits,
                    "message", "No items match “" + q + "”.");
        }
        StringBuilder msg = new StringBuilder("Found " + hits.size() + " item" + (hits.size() == 1 ? "" : "s") + ": ");
        int shown = 0;
        for (Map<String, Object> h : hits) {
            if (shown++ > 0) msg.append("; ");
            if (shown > 8) {
                msg.append("and ").append(hits.size() - 8).append(" more");
                break;
            }
            msg.append(h.get("sku")).append(" ").append(h.get("name"))
                    .append(" (").append(h.get("availableQty")).append(" available)");
        }
        return Map.of("ok", true, "tool", "search_items", "data", hits, "message", msg.toString());
    }

    private Map<String, Object> createPo(String actor, Map<String, Object> args) {
        requireAny("PURCHASE_ORDERS:CREATE");
        Product product = resolveProduct(str(args.get("product"), args.get("sku"), args.get("productId")));
        var supplier = suppliers.findAll().stream()
                .filter(s -> str(args.get("supplier")).isBlank()
                        || s.getName().toLowerCase(Locale.ROOT).contains(str(args.get("supplier")).toLowerCase(Locale.ROOT)))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No matching supplier."));
        var warehouse = warehouses.findAll().stream().filter(w -> w.isActive()).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No warehouse."));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("supplierId", supplier.getId());
        body.put("warehouseId", warehouse.getId());
        body.put("notes", firstNonBlank(str(args.get("notes")), "Created by agent"));
        body.put("lines", List.of(Map.of(
                "productId", product.getId(),
                "qty", num(args.get("qty"), "3"),
                "unitCost", num(args.get("unitCost"), product.getWholesalePrice() == null ? "10" : product.getWholesalePrice().toPlainString())
        )));
        Map<String, Object> po = procurement.createPo(actor, body);
        return Map.of("ok", true, "tool", "create_purchase_order", "data", po,
                "message", "Created " + po.get("poNo") + " for " + product.getName() + " with " + supplier.getName() + ".");
    }

    private Map<String, Object> editPo(String actor, Map<String, Object> args) {
        requireAny("PURCHASE_ORDERS:UPDATE");
        PurchaseOrder po = procurement.requirePo(str(args.get("poNo"), args.get("id")));
        Map<String, Object> body = new LinkedHashMap<>();
        if (!str(args.get("qty")).isBlank()) body.put("qty", args.get("qty"));
        if (!str(args.get("notes")).isBlank()) body.put("notes", args.get("notes"));
        if (!str(args.get("expectedDate")).isBlank()) body.put("expectedDate", args.get("expectedDate"));
        Map<String, Object> updated = procurement.updatePo(po.getId(), body, actor);
        return Map.of("ok", true, "tool", "edit_purchase_order", "data", updated,
                "message", "Updated " + updated.get("poNo") + ".");
    }

    private Map<String, Object> approveOrQuarantine(String actor, Map<String, Object> args) {
        PurchaseOrder po = procurement.requirePo(str(args.get("poNo"), args.get("id")));
        String action = str(args.get("action")).toUpperCase(Locale.ROOT);
        if (action.contains("QUARANTINE")) {
            requireAny("INVENTORY:UPDATE", "QUALITY:APPROVE", "PURCHASE_ORDERS:UPDATE");
        } else {
            requireAny("PURCHASE_ORDERS:APPROVE", "PURCHASE_ORDERS:UPDATE");
        }
        Map<String, Object> data = procurement.approveOrQuarantine(po.getId(), action, actor, "Agent");
        String msg = action.contains("QUARANTINE")
                ? "Received " + po.getPoNo() + " into quarantine" + (data.get("lot") != null ? " as lot " + data.get("lot") : "") + "."
                : "Approved " + po.getPoNo() + ".";
        return Map.of("ok", true, "tool", "approve_po_or_quarantine", "data", data, "message", msg);
    }

    private Map<String, Object> daily(Map<String, Object> args) {
        require("REPORTS:VIEW");
        LocalDate date = str(args.get("date")).isBlank() ? LocalDate.now() : LocalDate.parse(str(args.get("date")));
        Map<String, Object> report = reports.daily(date);
        return Map.of("ok", true, "tool", "generate_daily_report", "data", report, "message", summarize(report));
    }

    private Map<String, Object> monthly(Map<String, Object> args) {
        require("REPORTS:VIEW");
        int year = Integer.parseInt(str(args.get("year")).isBlank()
                ? String.valueOf(LocalDate.now().getYear()) : str(args.get("year")));
        int month = Integer.parseInt(str(args.get("month")).isBlank()
                ? String.valueOf(LocalDate.now().getMonthValue()) : str(args.get("month")));
        Map<String, Object> report = reports.monthly(year, month);
        return Map.of("ok", true, "tool", "generate_monthly_report", "data", report, "message", summarize(report));
    }

    private Map<String, Object> addCart(String username, Map<String, Object> args) {
        requireAny("AGENT:CREATE", "CART:CREATE", "CART:UPDATE");
        Product product = resolveProduct(str(args.get("product"), args.get("sku"), args.get("productId"), args.get("name")));
        BigDecimal qty = num(args.get("qty"), "1");
        Map<String, Object> cart = carts.add(username, product.getId(), qty);
        return Map.of("ok", true, "tool", "add_to_cart", "data", cart,
                "message", "Added " + qty.stripTrailingZeros().toPlainString() + " kg of " + product.getName()
                        + " (" + product.getSku() + ") to the cart. Cart total " + cart.get("total") + " AED.");
    }

    private Map<String, Object> createSalesOrder(String username, Map<String, Object> args) {
        requireAny("AGENT:CREATE", "CART:CREATE", "CART:UPDATE", "SALES_ORDERS:CREATE");
        String productHint = str(args.get("product"), args.get("sku"), args.get("productId"), args.get("name"));
        if (!productHint.isBlank()) {
            addCart(username, args);
        }
        UserAccount customer = users.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Sign in again to place an order."));
        String address = firstNonBlank(str(args.get("address"), args.get("shipToAddress")),
                customer.getCity() == null ? "" : customer.getCity() + ", UAE",
                "Dubai, UAE");
        String name = firstNonBlank(str(args.get("name"), args.get("shipToName")), customer.getFullName(), username);
        String phone = firstNonBlank(str(args.get("phone"), args.get("shipToPhone")), customer.getPhone(), "0500000000");
        Map<String, Object> order = orders.checkout(username, name, phone, address,
                "Placed by trading agent", null, null, "COD", false);
        return Map.of("ok", true, "tool", "create_sales_order", "data", order,
                "message", "Created sales order " + order.get("orderNo") + " for " + name
                        + ". Total " + order.get("total") + " AED, cash on delivery, shipping to " + address + ".");
    }

    private String summarize(Map<String, Object> report) {
        return kindLabel(report) + " " + report.get("label") + ": "
                + report.get("orderCount") + " orders, sales " + report.get("salesTotal")
                + " AED, " + report.get("poCount") + " POs, " + report.get("qcCount")
                + " QC. Open Reports to export Excel with a summary sheet then sales, invoices, POs and quality grids.";
    }

    private String kindLabel(Map<String, Object> report) {
        return "MONTHLY".equals(String.valueOf(report.get("kind"))) ? "Monthly report" : "Daily report";
    }

    private Product resolveProduct(String hint) {
        if (hint == null || hint.isBlank()) {
            throw new IllegalArgumentException("Which product? Try: add 2 kg of dates to cart");
        }
        String h = cleanProductHint(hint);
        if (h.isBlank()) {
            throw new IllegalArgumentException("Which product? Try: add 2 kg of dates to cart");
        }
        if (h.matches("\\d+")) {
            return products.findById(Long.valueOf(h)).orElseThrow(() -> new IllegalArgumentException("Product not found"));
        }
        return products.findBySku(h)
                .or(() -> products.findBySku(hint.trim()))
                .or(() -> products.findByNameContainingIgnoreCase(h).stream().findFirst())
                .or(() -> bestProductMatch(h))
                .orElseThrow(() -> new IllegalArgumentException("No product matches “" + h + "”. Search first, then use the sku."));
    }

    private java.util.Optional<Product> bestProductMatch(String hint) {
        String n = hint.toLowerCase(Locale.ROOT);
        Product best = null;
        int bestScore = 0;
        for (Product p : products.findAll()) {
            String name = p.getName() == null ? "" : p.getName().toLowerCase(Locale.ROOT);
            String sku = p.getSku() == null ? "" : p.getSku().toLowerCase(Locale.ROOT);
            int score = 0;
            if (name.equals(n) || sku.equalsIgnoreCase(hint)) score = 100;
            else if (name.contains(n) || sku.contains(n)) score = 50 + Math.min(n.length(), 20);
            else {
                for (String token : n.split("\\s+")) {
                    if (token.length() > 2 && (name.contains(token) || sku.contains(token))) {
                        score += token.length();
                    }
                }
            }
            if (score > bestScore) {
                bestScore = score;
                best = p;
            }
        }
        return bestScore >= 3 ? java.util.Optional.of(best) : java.util.Optional.empty();
    }

    private Category resolveCategory(String hint) {
        if (hint != null && hint.matches("\\d+")) {
            return categories.findById(Long.valueOf(hint)).orElseThrow(() -> new IllegalArgumentException("Category not found"));
        }
        if (hint != null && !hint.isBlank()) {
            return categories.findByName(hint).or(() ->
                            categories.findAll().stream()
                                    .filter(c -> c.getName().toLowerCase(Locale.ROOT).contains(hint.toLowerCase(Locale.ROOT)))
                                    .findFirst())
                    .orElseGet(() -> categories.findAll().stream().findFirst()
                            .orElseThrow(() -> new IllegalArgumentException("Add a category first.")));
        }
        return categories.findAll().stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Add a category first."));
    }

    private void require(String authority) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !RoleChecks.has(auth, authority)) {
            throw new AccessDeniedException(authority);
        }
    }

    private void requireAny(String... authorities) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !RoleChecks.hasAny(auth, authorities)) {
            throw new AccessDeniedException(authorities[0]);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> normalizeStep(Map<String, Object> step) {
        if (step == null) {
            return tool("", Map.of());
        }
        String name = str(step.get("name"), step.get("tool"), step.get("action"));
        if ("place_order".equals(name) || "checkout".equals(name) || "create_order".equals(name)
                || "place_sales_order".equals(name)) {
            name = "create_sales_order";
        }
        Object argsObj = step.get("args");
        if (argsObj == null) argsObj = step.get("arguments");
        if (argsObj == null) argsObj = step.get("parameters");
        Map<String, Object> args = argsObj instanceof Map<?, ?> m ? (Map<String, Object>) m : new LinkedHashMap<>();
        return tool(name, args);
    }

    private Map<String, Object> tool(String name, Map<String, Object> args) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("args", args);
        return m;
    }

    private Map<String, Object> reply(String message, List<Map<String, Object>> tools) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("message", message);
        m.put("tools", tools.stream().map(t -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", t.get("tool") == null ? t.get("name") : t.get("tool"));
            row.put("ok", t.getOrDefault("ok", true));
            row.put("message", t.get("message"));
            if (t.get("data") != null) row.put("data", t.get("data"));
            return row;
        }).toList());
        m.put("voice", false);
        return m;
    }

    public String promptGuide() {
        return help();
    }

    private boolean isHelp(String text) {
        String t = text.toLowerCase(Locale.ROOT).trim();
        return t.equals("help") || t.equals("hi") || t.equals("hello") || t.equals("formats")
                || t.equals("what can you do") || t.startsWith("help ") || t.contains("prompt format");
    }

    private String help() {
        return """
                Use these prompt formats:

                Add to cart
                • add 2 kg of dates to cart
                • add to cart sku FD-1 qty 2
                • put 3 Medjool dates in the basket

                Create / place a sales order (cash on delivery)
                • create order 2 kg dates
                • place order for sku FD-1 qty 2
                • create order 2 kg dates address Dubai Marina
                • checkout   (uses items already in your cart)

                Purchase order (procurement, not a customer order)
                • create PO 3 kg dates supplier Anatolia
                • create purchase order sku FD-1 qty 5

                Catalog
                • search dates
                • create product name Fard Dates sku FD-1 retail 28
                • edit product FD-1 retail 30
                • delete product FD-1

                Other
                • approve PO-123
                • quarantine PO-123
                • daily report
                • monthly report""";
    }

    private String extractSearchQuery(String raw) {
        String quoted = extractQuoted(raw);
        if (!quoted.isBlank()) return quoted;
        String s = raw.trim();
        s = s.replaceFirst("(?i)^(please\\s+)?(search|find|look\\s*up|lookup|show)\\s+", "");
        s = s.replaceFirst("(?i)^(for\\s+)?(the\\s+)?(items?|products?|skus?)\\s+", "");
        s = s.replaceFirst("(?i)^(named|called|matching)\\s+", "");
        return s.replaceAll("[?.!]+$", "").trim();
    }

    private String extractPo(String raw) {
        Matcher m = PO_REF.matcher(raw);
        return m.find() ? m.group(1).toUpperCase(Locale.ROOT) : "";
    }

    private String extractSku(String raw) {
        Matcher m = Pattern.compile("sku\\s*[:=]?\\s*([A-Za-z0-9._-]+)", Pattern.CASE_INSENSITIVE).matcher(raw);
        return m.find() ? m.group(1) : "";
    }

    private String extractQty(String raw, String fallback) {
        Matcher m = Pattern.compile("(?:qty|quantity|kg)\\s*[:=]?\\s*(\\d+(?:\\.\\d+)?)", Pattern.CASE_INSENSITIVE).matcher(raw);
        if (m.find()) return m.group(1);
        Matcher n = Pattern.compile("\\b(\\d+(?:\\.\\d+)?)\\s*(?:kg|kgs|of)\\b", Pattern.CASE_INSENSITIVE).matcher(raw);
        if (n.find()) return n.group(1);
        Matcher lead = Pattern.compile("(?:add|put|buy|order|place)\\s+(\\d+(?:\\.\\d+)?)", Pattern.CASE_INSENSITIVE).matcher(raw);
        if (lead.find()) return lead.group(1);
        Matcher any = Pattern.compile("\\b(\\d+(?:\\.\\d+)?)\\b").matcher(raw);
        return any.find() ? any.group(1) : fallback;
    }

    private String extractDate(String raw) {
        Matcher m = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})").matcher(raw);
        return m.find() ? m.group(1) : LocalDate.now().toString();
    }

    private YearMonth extractYearMonth(String raw) {
        Matcher m = Pattern.compile("(\\d{4})-(\\d{2})").matcher(raw);
        if (m.find()) return YearMonth.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));
        return YearMonth.now();
    }

    private String extractKind(String raw) {
        String t = raw.toLowerCase(Locale.ROOT);
        if (t.contains("fresh")) return "FRESH_FRUIT";
        if (t.contains("nut")) return "NUT";
        if (t.contains("mix")) return "MIX";
        return "DRY_FRUIT";
    }

    private String extractProductHint(String raw) {
        String q = extractQuoted(raw);
        if (!q.isBlank()) return q;
        String sku = extractSku(raw);
        if (!sku.isBlank()) return sku;
        Matcher m = Pattern.compile("(?:product|of|sku)\\s+([A-Za-z0-9][A-Za-z0-9 ._-]{1,40})", Pattern.CASE_INSENSITIVE).matcher(raw);
        if (m.find()) return cleanProductHint(m.group(1));
        return "";
    }

    private String extractTradeProductHint(String raw) {
        String q = extractQuoted(raw);
        if (!q.isBlank()) return q;
        String sku = extractSku(raw);
        if (!sku.isBlank()) return sku;
        String trimmed = raw.replaceFirst("(?i)(?:address|ship(?:ping)?\\s*to|deliver(?:y)?\\s*to)\\s*[:=]?\\s*.+$", "");
        Matcher of = Pattern.compile("\\bof\\s+(.+?)(?:\\s+(?:to|in|into)\\s+(?:the\\s+)?(?:my\\s+)?(?:cart|basket)|$)", Pattern.CASE_INSENSITIVE).matcher(trimmed);
        if (of.find()) return cleanProductHint(of.group(1));
        Matcher named = Pattern.compile("(?:named|called|product)\\s+([A-Za-z0-9][A-Za-z0-9 ._-]{1,60})", Pattern.CASE_INSENSITIVE).matcher(trimmed);
        if (named.find()) return cleanProductHint(named.group(1));
        return cleanProductHint(trimmed);
    }

    private String cleanProductHint(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        s = s.replaceAll("(?i)\\b(please|kindly)\\b", " ");
        s = s.replaceAll("(?i)\\b(add|put|buy|order|place|create|an|a|the|to|my|our|into|in|for|me)\\b", " ");
        s = s.replaceAll("(?i)\\b(cart|basket|checkout|sales|purchase|kg|kgs|qty|quantity|sku|address)\\b", " ");
        s = s.replaceAll("\\d+(?:\\.\\d+)?", " ");
        s = s.replaceAll("[?:!,]+", " ");
        return s.replaceAll("\\s+", " ").trim();
    }

    private String extractAddress(String raw) {
        Matcher m = Pattern.compile("(?:address|ship(?:ping)?\\s*to|deliver(?:y)?\\s*to)\\s*[:=]?\\s*(.+)$", Pattern.CASE_INSENSITIVE).matcher(raw.trim());
        return m.find() ? m.group(1).trim() : "";
    }

    private String extractQuoted(String raw) {
        Matcher m = Pattern.compile("[\"“]([^\"]+)[\"”]").matcher(raw);
        return m.find() ? m.group(1) : "";
    }

    private String extractAfter(String raw, String key) {
        Matcher m = Pattern.compile(key + "\\s*[:=]?\\s*([A-Za-z0-9][A-Za-z0-9 ._-]{0,40})", Pattern.CASE_INSENSITIVE).matcher(raw);
        return m.find() ? m.group(1).trim() : "";
    }

    private String extractNumberAfter(String raw, String key, String fallback) {
        Matcher m = Pattern.compile(key + "\\s*[:=]?\\s*(\\d+(?:\\.\\d+)?)", Pattern.CASE_INSENSITIVE).matcher(raw);
        return m.find() ? m.group(1) : fallback;
    }

    private void putIfNumber(Map<String, Object> args, String key, String value) {
        if (value != null && !value.isBlank()) args.put(key, value);
    }

    private String str(Object... vals) {
        for (Object v : vals) {
            if (v != null && !String.valueOf(v).isBlank() && !"null".equals(String.valueOf(v))) {
                return String.valueOf(v).trim();
            }
        }
        return "";
    }

    private String firstNonBlank(String... vals) {
        for (String v : vals) {
            if (v != null && !v.isBlank()) return v.trim();
        }
        return "";
    }

    private BigDecimal num(Object v, String fallback) {
        String s = str(v);
        if (s.isBlank()) s = fallback;
        return new BigDecimal(s);
    }
}
