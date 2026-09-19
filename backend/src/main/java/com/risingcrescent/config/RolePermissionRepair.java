package com.risingcrescent.config;

import com.risingcrescent.domain.entity.Role;
import com.risingcrescent.domain.entity.Screen;
import com.risingcrescent.repo.RoleRepository;
import com.risingcrescent.repo.ScreenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class RolePermissionRepair implements CommandLineRunner {
    private final RoleRepository roles;
    private final ScreenRepository screens;

    @Override
    @Transactional
    public void run(String... args) {
        ensureScreen("REPORTS", "Daily and monthly reports", "Console", "/console/reports", 145);
        ensureScreen("AGENT", "Agent chat", "Platform", "/", 148);
        ensureScreen("OPS", "Operations", "Admin", "/console/ops", 156);

        for (String code : List.of("WAREHOUSE_KEEPER", "PROCUREMENT_OFFICER", "TRADING_MANAGER")) {
            grant(code, "INVENTORY:VIEW", "INVENTORY:UPDATE", "INVENTORY:CREATE",
                    "REPORTS:VIEW", "AGENT:VIEW", "AGENT:CREATE");
        }
        grant("QUALITY_INSPECTOR", "INVENTORY:VIEW", "INVENTORY:UPDATE", "REPORTS:VIEW", "AGENT:VIEW", "AGENT:CREATE");
        grant("SALES_EXECUTIVE", "REPORTS:VIEW", "AGENT:VIEW", "AGENT:CREATE");
        grant("SALES_MANAGER", "REPORTS:VIEW", "AGENT:VIEW", "AGENT:CREATE");
        grant("FINANCE_OFFICER", "REPORTS:VIEW", "AGENT:VIEW", "AGENT:CREATE");
        grant("CATALOG_MERCHANDISER", "AGENT:VIEW", "AGENT:CREATE");
        grant("CUSTOMER", "AGENT:VIEW", "AGENT:CREATE", "CART:CREATE", "CART:UPDATE");
        grant("SUPPLIER", "INVENTORY:VIEW", "AGENT:VIEW", "AGENT:CREATE");
        grant("SUPER_ADMIN", "REPORTS:VIEW", "REPORTS:CREATE", "AGENT:VIEW", "AGENT:CREATE", "AGENT:UPDATE",
                "OPS:VIEW", "OPS:UPDATE", "OPS:CREATE");
        grant("TRADING_MANAGER", "PURCHASE_ORDERS:APPROVE", "QUALITY:APPROVE");
    }

    private void ensureScreen(String code, String name, String module, String path, int sort) {
        if (screens.existsByCode(code)) {
            return;
        }
        screens.save(Screen.builder().code(code).name(name).module(module).path(path).sortOrder(sort).build());
        log.info("Added screen {}", code);
    }

    private void grant(String roleCode, String... perms) {
        roles.findByCode(roleCode).ifPresent(role -> {
            Set<String> set = role.getPermissions();
            boolean changed = false;
            for (String p : perms) {
                changed |= set.add(p);
            }
            if (changed) {
                roles.save(role);
                log.info("Granted extra permissions to {}", roleCode);
            }
        });
    }
}
