package com.risingcrescent.config;

import com.risingcrescent.domain.entity.Role;
import com.risingcrescent.domain.entity.Supplier;
import com.risingcrescent.domain.entity.UserAccount;
import com.risingcrescent.domain.enums.Channel;
import com.risingcrescent.repo.RoleRepository;
import com.risingcrescent.repo.SupplierRepository;
import com.risingcrescent.repo.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class RoleCatalogRepair implements CommandLineRunner {
    private final RoleRepository roles;
    private final UserAccountRepository users;
    private final SupplierRepository suppliers;
    private final PasswordEncoder encoder;

    @Override
    @Transactional
    public void run(String... args) {
        Role supplier = upsertRole("SUPPLIER", "Grower / packer portal", "Assigned POs, acknowledge, view your lots and QC",
                screen("DASHBOARD", "VIEW"),
                screen("PURCHASE_ORDERS", "VIEW", "UPDATE"),
                screen("PRODUCTS", "VIEW"),
                screen("QUALITY", "VIEW"),
                screen("INVENTORY", "VIEW"),
                screen("NOTIFICATIONS", "VIEW"),
                screen("WORKFLOWS", "VIEW"),
                screen("AGENT", "VIEW", "CREATE"));
        Role sales = upsertRole("SALES_EXECUTIVE", "Wholesale sales", "B2B quotes, cart, order confirmation",
                screen("DASHBOARD", "VIEW"),
                screen("STORE", "VIEW"),
                screen("CART", "VIEW", "CREATE", "UPDATE"),
                screen("MY_ORDERS", "VIEW", "CREATE"),
                screen("SALES_ORDERS", "VIEW", "CREATE", "UPDATE", "APPROVE"),
                screen("PRODUCTS", "VIEW"),
                screen("INVENTORY", "VIEW"),
                screen("INVOICES", "VIEW"),
                screen("USERS", "VIEW"),
                screen("NOTIFICATIONS", "VIEW"));
        Role salesMgr = upsertRole("SALES_MANAGER", "Sales manager", "Wholesale desk, credit hold release, invoice follow-up",
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
                screen("WORKFLOWS", "VIEW"));

        ensureUser("supplier", "Supp@123", "Anatolia Packers", "supplier@example.com", Channel.WHOLESALE, supplier);
        ensureUser("kashmir", "Kashmir@123", "Kashmir Orchard Co", "farah@kashmir.test", Channel.WHOLESALE, supplier);
        ensureUser("dates", "Dates@123", "Al Batinah Dates", "salim@dates.test", Channel.WHOLESALE, supplier);
        ensureUser("sales", "Sales@123", "Hana Wholesale", "sales@risingcrescent.com", Channel.WHOLESALE, sales);
        ensureUser("salesmgr", "SalesMgr@123", "Nour Sales Desk", "salesmgr@risingcrescent.com", Channel.WHOLESALE, salesMgr);

        linkPortal("Anatolia Packers", "supplier");
        linkPortal("Kashmir Orchard Co", "kashmir");
        linkPortal("Al Batinah Dates", "dates");
    }

    @SafeVarargs
    private Role upsertRole(String code, String name, String description, Set<String>... screens) {
        Set<String> perms = new LinkedHashSet<>();
        for (Set<String> s : screens) perms.addAll(s);
        Role role = roles.findByCode(code).orElseGet(() -> Role.builder().code(code).name(name).description(description).permissions(new HashSet<>()).build());
        role.setName(name);
        role.setDescription(description);
        boolean changed = role.getPermissions().addAll(perms);
        roles.save(role);
        if (role.getId() == null || changed) {
            log.info("Completed role {}", code);
        }
        return role;
    }

    private Set<String> screen(String code, String... actions) {
        Set<String> p = new LinkedHashSet<>();
        for (String a : actions) p.add(code + ":" + a);
        return p;
    }

    private void ensureUser(String username, String password, String fullName, String email, Channel channel, Role role) {
        UserAccount u = users.findByUsername(username).orElseGet(() -> UserAccount.builder()
                .username(username)
                .passwordHash(encoder.encode(password))
                .fullName(fullName)
                .email(email)
                .active(true)
                .preferredChannel(channel)
                .roles(new HashSet<>())
                .creditLimit(BigDecimal.ZERO)
                .outstandingCredit(BigDecimal.ZERO)
                .build());
        if (u.getRoles() == null) u.setRoles(new HashSet<>());
        boolean added = u.getRoles().add(role);
        if (u.getId() == null || added) {
            users.save(u);
            log.info("Ensured {} user {}", role.getCode(), username);
        }
    }

    private void linkPortal(String supplierName, String username) {
        UserAccount user = users.findByUsername(username).orElse(null);
        if (user == null) return;
        suppliers.findAll().stream()
                .filter(s -> supplierName.equalsIgnoreCase(s.getName()))
                .findFirst()
                .ifPresent(s -> {
                    if (s.getPortalUser() == null || !username.equals(s.getPortalUser().getUsername())) {
                        s.setPortalUser(user);
                        suppliers.save(s);
                        log.info("Linked supplier {} to login {}", supplierName, username);
                    }
                });
    }
}
