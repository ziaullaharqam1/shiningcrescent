package com.risingcrescent.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Order(0)
@RequiredArgsConstructor
public class SchemaRepair implements CommandLineRunner {
    private final JdbcTemplate jdbc;

    @Override
    public void run(String... args) {
        sql("alter table if exists sales_orders drop constraint if exists sales_orders_status_check");
        sql("alter table if exists sales_orders drop constraint if exists sales_orders_payment_status_check");
        sql("alter table if exists deliveries drop constraint if exists deliveries_status_check");
        sql("alter table if exists payments alter column invoice_id drop not null");
        sql("create unique index if not exists users_phone_uidx on users (phone) where phone is not null and phone <> ''");
        sql("alter table if exists products add column if not exists image_url varchar(255)");
        sql("alter table if exists sales_orders add column if not exists leave_at_door boolean default false");
    }

    private void sql(String statement) {
        try {
            jdbc.execute(statement);
        } catch (Exception ex) {
            log.debug("schema repair skipped: {} ({})", statement, ex.getMessage());
        }
    }
}
