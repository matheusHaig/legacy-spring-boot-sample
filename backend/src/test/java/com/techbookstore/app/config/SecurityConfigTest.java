package com.techbookstore.app.config;

import com.techbookstore.app.dto.DashboardKpiDto;
import com.techbookstore.app.service.BatchProcessingService;
import com.techbookstore.app.service.ReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;

@SpringBootTest(properties = "management.health.redis.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityConfigTest {

    private static final String PROTECTED_ENDPOINT =
            "/api/v1/reports/dashboard/kpis";

    private static final String ADMIN_ENDPOINT =
            "/api/v1/reports/admin/batch/daily";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReportService reportService;

    @MockBean
    private BatchProcessingService batchProcessingService;

    @Value("${API_USER_PASSWORD}")
    private String userPassword;

    @Value("${API_ADMIN_PASSWORD}")
    private String adminPassword;

    @BeforeEach
    void setUp() {
        when(reportService.generateDashboardKpis())
                .thenReturn(new DashboardKpiDto(LocalDate.now()));
    }

    // Authentication

    @Test
    void shouldReturnUnauthorizedWithoutCredentials() throws Exception {
        mockMvc.perform(get(PROTECTED_ENDPOINT))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("WWW-Authenticate"));
    }

    @Test
    void shouldReturnUnauthorizedWithInvalidPassword() throws Exception {
        mockMvc.perform(get(PROTECTED_ENDPOINT)
                        .with(httpBasic("user", "invalid-password")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturnUnauthorizedWithInvalidUsername() throws Exception {
        mockMvc.perform(get(PROTECTED_ENDPOINT)
                        .with(httpBasic("invalid-user", userPassword)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldAllowAccessWithValidCredentials() throws Exception {
        mockMvc.perform(get(PROTECTED_ENDPOINT)
                        .with(httpBasic("user", userPassword)))
                .andExpect(status().isOk());
    }

    // Authorization

    @Test
    void shouldRejectUnauthenticatedAccessToAdminEndpoint() throws Exception {
        mockMvc.perform(post(ADMIN_ENDPOINT)
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectRegularUserAccessToAdminEndpoint() throws Exception {
        mockMvc.perform(post(ADMIN_ENDPOINT)
                        .with(httpBasic("user", userPassword))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldAllowAdminAccessToAdminEndpoint() throws Exception {
        mockMvc.perform(post(ADMIN_ENDPOINT)
                        .with(httpBasic("admin", adminPassword))
                        .with(csrf()))
                .andExpect(status().isOk());
    }

    // CSRF protection

    @Test
    void shouldRejectPostWithoutCsrfToken() throws Exception {
        mockMvc.perform(post(ADMIN_ENDPOINT)
                        .with(httpBasic("admin", adminPassword)))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldRejectPostWithInvalidCsrfToken() throws Exception {
        mockMvc.perform(post(ADMIN_ENDPOINT)
                        .with(httpBasic("admin", adminPassword))
                        .with(csrf().useInvalidToken()))
                .andExpect(status().isForbidden());
    }

    // Public endpoints

    @Test
    void shouldAllowPublicAccessToSwagger() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldAllowPublicAccessToApiDocs() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldAllowPublicAccessToHealthEndpoint() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    // H2 Console

    @Test
    void shouldRejectH2ConsoleOutsideDevProfile() throws Exception {
        mockMvc.perform(get("/h2-console/")
                        .with(httpBasic("admin", adminPassword)))
                .andExpect(status().isForbidden());
    }

    // Stateless authentication

    @Test
    void shouldNotCreateSessionForAuthenticatedGetRequest() throws Exception {
        mockMvc.perform(get(PROTECTED_ENDPOINT)
                        .with(httpBasic("user", userPassword)))
                .andExpect(status().isOk())
                .andExpect(result ->
                        assertNull(result.getRequest().getSession(false)));
    }

    @Test
    void shouldRequireAuthenticationForEveryRequest() throws Exception {
        mockMvc.perform(get(PROTECTED_ENDPOINT)
                        .with(httpBasic("user", userPassword)))
                .andExpect(status().isOk());

        mockMvc.perform(get(PROTECTED_ENDPOINT))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldAllowConfiguredCorsOrigin() throws Exception {
        mockMvc.perform(get(PROTECTED_ENDPOINT)
                        .with(httpBasic("user", userPassword))
                        .header("Origin", "http://localhost:3000"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        "Access-Control-Allow-Origin",
                        "http://localhost:3000"));
    }

    @Test
    void shouldRejectUnauthorizedCorsOrigin() throws Exception {
        mockMvc.perform(get(PROTECTED_ENDPOINT)
                        .with(httpBasic("user", userPassword))
                        .header("Origin", "https://malicious.example"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(
                        "Access-Control-Allow-Origin"));
    }

    @Test
    void shouldAllowPreflightWithoutAuthentication() throws Exception {
        mockMvc.perform(options(PROTECTED_ENDPOINT)
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        "Access-Control-Allow-Origin",
                        "http://localhost:3000"));
    }

    @Test
    void shouldRejectUnauthorizedPreflight() throws Exception {
        mockMvc.perform(options(PROTECTED_ENDPOINT)
                        .header("Origin", "https://malicious.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }
}