package com.example.dmsyncbridge.controller;

import com.example.dmsyncbridge.entity.SyncConfig;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "dm.sync.scheduler.enabled=false")
class ConfigControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testConfigCrudLifecycle() throws Exception {
        SyncConfig request = new SyncConfig();
        request.setTableName("person");
        request.setPrimaryKey("id");
        request.setIncludeColumns(java.util.Arrays.asList("name", "email"));

        mockMvc.perform(post("/config/tables")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        String listResponse = mockMvc.perform(get("/config/tables"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<SyncConfig> configs = objectMapper.readValue(listResponse, new TypeReference<List<SyncConfig>>() {
        });
        assertThat(configs).hasSize(1);
        assertThat(configs.get(0).getTableName()).isEqualTo("person");

        mockMvc.perform(delete("/config/tables/person"))
                .andExpect(status().isNoContent());

        String emptyResponse = mockMvc.perform(get("/config/tables"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<SyncConfig> empty = objectMapper.readValue(emptyResponse, new TypeReference<List<SyncConfig>>() {
        });
        assertThat(empty).isEmpty();
    }
}