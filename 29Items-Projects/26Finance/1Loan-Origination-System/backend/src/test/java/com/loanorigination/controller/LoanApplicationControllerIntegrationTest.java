package com.loanorigination.controller;

import com.loanorigination.dto.LoanApplicationDto;
import com.loanorigination.model.LoanApplication;
import com.loanorigination.repository.LoanApplicationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("test")
class LoanApplicationControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LoanApplicationRepository repository;

    private LoanApplicationDto validApplicationDto;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        
        validApplicationDto = new LoanApplicationDto();
        validApplicationDto.setLoanAmount(new BigDecimal("50000"));
        validApplicationDto.setLoanPurpose("Home Purchase");
        validApplicationDto.setLoanTermMonths(360);
        validApplicationDto.setApplicantId(1L);
    }

    @Test
    void submitApplication_ValidData_ShouldReturn201() throws Exception {
        mockMvc.perform(post("/api/applications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validApplicationDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.applicationId").exists())
                .andExpect(jsonPath("$.loanAmount").value(50000))
                .andExpect(jsonPath("$.status").value("SUBMITTED"));
    }

    @Test
    void submitApplication_InvalidAmount_ShouldReturn400() throws Exception {
        validApplicationDto.setLoanAmount(new BigDecimal("-1000"));

        mockMvc.perform(post("/api/applications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validApplicationDto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getApplication_ExistingId_ShouldReturn200() throws Exception {
        LoanApplication saved = createTestApplication();

        mockMvc.perform(get("/api/applications/" + saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(saved.getId()))
                .andExpect(jsonPath("$.applicationId").value(saved.getApplicationId()));
    }

    @Test
    void getApplication_NonExistingId_ShouldReturn404() throws Exception {
        mockMvc.perform(get("/api/applications/99999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getAllApplications_ShouldReturnList() throws Exception {
        createTestApplication();
        createTestApplication();

        mockMvc.perform(get("/api/applications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(2))));
    }

    private LoanApplication createTestApplication() {
        LoanApplication application = new LoanApplication();
        application.setApplicationId("LA-TEST-" + System.currentTimeMillis());
        application.setLoanAmount(new BigDecimal("50000"));
        application.setLoanPurpose("Home Purchase");
        application.setLoanTermMonths(360);
        application.setApplicantId(1L);
        application.setStatus(LoanApplication.ApplicationStatus.SUBMITTED);
        return repository.save(application);
    }
}
