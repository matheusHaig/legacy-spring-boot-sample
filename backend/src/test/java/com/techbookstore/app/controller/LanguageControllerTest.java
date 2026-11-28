package com.techbookstore.app.controller;

import com.techbookstore.app.config.SecurityConfig;
import com.techbookstore.app.config.WebMvcConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(LanguageController.class)
@Import({SecurityConfig.class, WebMvcConfig.class})
@ActiveProfiles("test")
@WithMockUser(roles = "USER")
class LanguageControllerTest {

    private static final String ENDPOINT = "/language/switch";

    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest
    @CsvSource({
            "/books, /books",
            "/books?page=2, /books?page=2",
            "http://localhost/books, /books",
            "http://localhost:80/books, /books",
            "http://localhost/books?page=2, /books?page=2"
    })
    void shouldAllowSafeRedirect(
            String referer, String expectedRedirect) throws Exception {

        mockMvc.perform(get(ENDPOINT)
                        .param("lang", "en")
                        .header("Referer", referer))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(expectedRedirect));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://malicious.example",
            "//malicious.example",
            "///malicious.example",
            "http://localhost:9090/books",
            "https://localhost/books",
            "http://localhost.malicious.example/books",
            "http://localhost@malicious.example/books",
            "javascript:alert(1)",
            "/%2Fmalicious.example",
            "/%5Cmalicious.example",
            "/books/../orders",
            "https://[invalid",
            "books",
            ""
    })
    void shouldRejectUnsafeRedirect(String referer) throws Exception {
        mockMvc.perform(get(ENDPOINT)
                        .param("lang", "en")
                        .header("Referer", referer))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void shouldRedirectToHomeWhenRefererIsMissing() throws Exception {
        mockMvc.perform(get(ENDPOINT)
                        .param("lang", "en"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void shouldPreserveLanguageSwitching() throws Exception {
        mockMvc.perform(get(ENDPOINT)
                        .param("lang", "en")
                        .header("Referer", "/books"))
                .andExpect(status().is3xxRedirection())
                .andExpect(cookie().value("language", "en"))
                .andExpect(redirectedUrl("/books"));
    }
}