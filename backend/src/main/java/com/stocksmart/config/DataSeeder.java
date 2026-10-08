package com.stocksmart.config;

import com.stocksmart.entity.Role;
import com.stocksmart.entity.RoleName;
import com.stocksmart.entity.User;
import com.stocksmart.repository.RoleRepository;
import com.stocksmart.repository.UserRepository;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Seeds the six canonical roles + demo users (admin + manager).
 * Controlled by {@code app.data.seed-enabled}; demo password comes from
 * {@code APP_SEED_PASSWORD} so no real secret lives in source control.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private static final Map<RoleName, String> ROLE_DESCRIPTIONS = Map.of(
            RoleName.ADMIN, "Full system administration",
            RoleName.INVENTORY_MANAGER, "Inventory planning and oversight",
            RoleName.WAREHOUSE_STAFF, "Warehouse operations",
            RoleName.PROCUREMENT_USER, "Supplier and purchase operations",
            RoleName.SALES_USER, "Sales operations",
            RoleName.MANAGEMENT, "Management reporting and oversight");

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final boolean seedEnabled;
    private final String defaultPassword;

    public DataSeeder(
            RoleRepository roleRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.data.seed-enabled:true}") boolean seedEnabled,
            @Value("${app.data.default-password:Admin@123}") String defaultPassword) {
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.seedEnabled = seedEnabled;
        this.defaultPassword = defaultPassword;
    }

    @Override
    public void run(String... args) {
        if (!seedEnabled) {
            log.info("Data seeding disabled (app.data.seed-enabled=false)");
            return;
        }
        for (RoleName roleName : RoleName.values()) {
            roleRepository.findByName(roleName).orElseGet(() -> roleRepository.save(
                    new Role(roleName, ROLE_DESCRIPTIONS.get(roleName))));
        }
        createDemoUser("admin", "admin@stocksmart.local", "System Administrator", RoleName.ADMIN);
        createDemoUser("manager", "manager@stocksmart.local", "Management User", RoleName.MANAGEMENT);
        log.info("StockSmart Day 1 seed complete: {} roles, {} users",
                roleRepository.count(), userRepository.count());
    }

    private void createDemoUser(String username, String email, String fullName, RoleName roleName) {
        if (userRepository.existsByUsername(username)) {
            return;
        }
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new IllegalStateException("Role not seeded: " + roleName));
        User user = new User(username, email, passwordEncoder.encode(defaultPassword), fullName, role);
        userRepository.save(user);
        log.info("Seeded demo user '{}' with role {}", username, roleName);
    }
}
