package com.sro.myportfoliotracker.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class SecurityConfigTest {

  @Autowired
  private ApplicationContext context;

  @Test
  void contextLoads_withSecurity() {
    assertNotNull(context);
  }

  @Test
  void securityFilterChain_isConfigured() {
    // Verify that the SecurityFilterChain bean exists (auto-configured by Spring Boot)
    assertTrue(context.containsBean("securityFilterChain"),
        "SecurityFilterChain should be auto-configured");
  }
}
