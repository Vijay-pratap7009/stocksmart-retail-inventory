package com.stocksmart;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * StockSmart — Retail Inventory Optimization &amp; Supply Chain Management.
 * Day 1: modular-monolith foundation (auth + Oracle + JWT + RBAC).
 */
@SpringBootApplication
public class StockSmartApplication {

    public static void main(String[] args) {
        SpringApplication.run(StockSmartApplication.class, args);
    }
}
