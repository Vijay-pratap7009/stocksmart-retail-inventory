package com.stocksmart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stocksmart.entity.Role;
import com.stocksmart.entity.RoleName;
import com.stocksmart.entity.User;
import com.stocksmart.repository.RoleRepository;
import com.stocksmart.repository.UserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Day 1 security matrix: health (public), login (200/401),
 * protected (401/200), expired/invalid JWT (401), admin + management RBAC (403/200).
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityVerificationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Value("${security.jwt.secret}")
    private String jwtSecret;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        roleRepository.deleteAll();
        Role adminRole = roleRepository.save(new Role(RoleName.ADMIN, "admin"));
        Role mgmtRole = roleRepository.save(new Role(RoleName.MANAGEMENT, "mgmt"));
        userRepository.save(new User("admin", "admin@t.local",
                passwordEncoder.encode("Admin@123"), "Admin", adminRole));
        userRepository.save(new User("manager", "manager@t.local",
                passwordEncoder.encode("Admin@123"), "Manager", mgmtRole));
    }

    @Test
    void healthIsPublic() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.application").value("StockSmart"));
    }

    @Test
    void validCredentialsReturnJwt() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"Admin@123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("token").asText().split("\\.")).hasSize(3);
        assertThat(body.has("password")).isFalse();
        assertThat(body.has("passwordHash")).isFalse();
    }

    @Test
    void invalidCredentialsReturn401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void protectedEndpointMatrix() throws Exception {
        String adminToken = login("admin");
        String managerToken = login("manager");

        // Missing JWT → 401
        mockMvc.perform(get("/api/v1/test/protected")).andExpect(status().isUnauthorized());

        // Authenticated → 200
        mockMvc.perform(get("/api/v1/test/protected")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // Non-admin → 403 on /admin; admin → 200
        mockMvc.perform(get("/api/v1/test/admin")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/test/admin")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // Non-management → 403 on /management; management → 200
        mockMvc.perform(get("/api/v1/test/management")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/test/management")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk());

        // Garbage JWT → 401
        mockMvc.perform(get("/api/v1/test/protected")
                        .header("Authorization", "Bearer invalid.token.here"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredJwtIsRejected() throws Exception {
        // Token signed with the real key but already past its expiration.
        String expired = Jwts.builder()
                .subject("admin")
                .claim("uid", 1L)
                .claim("role", "ADMIN")
                .issuedAt(new Date(System.currentTimeMillis() - 120_000))
                .expiration(new Date(System.currentTimeMillis() - 60_000))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret)))
                .compact();
        mockMvc.perform(get("/api/v1/test/protected")
                        .header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void passwordIsStoredAsBcryptHash() {
        User admin = userRepository.findByUsername("admin").orElseThrow();
        assertThat(admin.getPasswordHash()).isNotEqualTo("Admin@123");
        assertThat(passwordEncoder.matches("Admin@123", admin.getPasswordHash())).isTrue();
    }

    private String login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"Admin@123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("token").asText();
    }
}
