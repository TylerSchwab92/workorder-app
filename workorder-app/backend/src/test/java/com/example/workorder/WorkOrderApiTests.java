package com.example.workorder;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the Work Order API.

 * Tests:
 *   1. Creating a work order returns the right defaults.
 *   2. A blank title gets rejected, and nothing gets saved.
 *   3. The list endpoint shows what we created, and the status filter works.
 *   4. Looking up an id that doesn't exist returns 404.
 *   5. Changing status actually updates the row (and updatedAt moves).
 *   6. A garbage status value gets rejected.
 *   7. You can't reopen a work order that's already completed.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WorkOrderApiTests {

    @Value("${local.server.port}")
    private int port;

    private final TestRestTemplate rest = new TestRestTemplate(
        new org.springframework.boot.web.client.RestTemplateBuilder()
                .requestFactory(() -> new org.springframework.http.client.HttpComponentsClientHttpRequestFactory())
            );

    @Test
    @Order(1)
    void createValidWorkOrder_returns201WithDefaults() {
        ResponseEntity<Map> response = postJson("/api/workorders",
                Map.of("title", "Fix pump", "description", "leaking", "assignedTo", "alice"));

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        Map<?, ?> body = response.getBody();
        assertNotNull(body);
        assertEquals("Fix pump", body.get("title"));
        assertEquals("OPEN", body.get("status"));
        assertEquals(body.get("createdAt"), body.get("updatedAt"));
    }

    @Test
    @Order(2)
    void createBlankTitle_returns400AndDoesNotPersist() {
        int before = listCount();

        ResponseEntity<Map> response = postJson("/api/workorders", Map.of("title", "   "));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().containsKey("error"));
        assertEquals(before, listCount());
    }

    @Test
    @Order(3)
    void list_reflectsCreatedOrders_andFiltersByStatus() {
        postJson("/api/workorders", Map.of("title", "Order A"));
        ResponseEntity<Map> created = postJson("/api/workorders", Map.of("title", "Order B"));
        long id = idOf(created);
        patchJson("/api/workorders/" + id, Map.of("status", "IN_PROGRESS"));

        ResponseEntity<Map> filtered = rest.getForEntity(url("/api/workorders?status=IN_PROGRESS"), Map.class);

        assertEquals(HttpStatus.OK, filtered.getStatusCode());
        assertTrue(((Number) filtered.getBody().get("count")).intValue() >= 1);
    }

    @Test
    @Order(4)
    void getById_unknownId_returns404() {
        ResponseEntity<Map> response = rest.getForEntity(url("/api/workorders/999999"), Map.class);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    @Order(5)
    void patchStatus_validTransition_returns200AndBumpsUpdatedAt() throws InterruptedException {
        ResponseEntity<Map> created = postJson("/api/workorders", Map.of("title", "Needs work"));
        long id = idOf(created);
        String createdAt = (String) created.getBody().get("createdAt");

        Thread.sleep(5); // make sure the clock actually moves before we update
        ResponseEntity<Map> response = patchJson("/api/workorders/" + id, Map.of("status", "IN_PROGRESS"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("IN_PROGRESS", response.getBody().get("status"));
        assertNotEquals(createdAt, response.getBody().get("updatedAt"));
    }

    @Test
    @Order(6)
    void patchStatus_invalidValue_returns400() {
        ResponseEntity<Map> created = postJson("/api/workorders", Map.of("title", "Needs work"));
        long id = idOf(created);

        ResponseEntity<Map> response = patchJson("/api/workorders/" + id, Map.of("status", "NOT_A_REAL_STATUS"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    @Order(7)
    void patchStatus_onTerminalWorkOrder_returns409() {
        ResponseEntity<Map> created = postJson("/api/workorders", Map.of("title", "Will be completed"));
        long id = idOf(created);

        ResponseEntity<Map> complete = patchJson("/api/workorders/" + id, Map.of("status", "COMPLETED"));
        assertEquals(HttpStatus.OK, complete.getStatusCode());

        ResponseEntity<Map> reopen = patchJson("/api/workorders/" + id, Map.of("status", "OPEN"));
        assertEquals(HttpStatus.CONFLICT, reopen.getStatusCode());
    }

    // helpers

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private ResponseEntity<Map> postJson(String path, Map<String, Object> body) {
        return rest.postForEntity(url(path), jsonEntity(body), Map.class);
    }

    private ResponseEntity<Map> patchJson(String path, Map<String, Object> body) {
        return rest.exchange(url(path), HttpMethod.PATCH, jsonEntity(body), Map.class);
    }

    private HttpEntity<Map<String, Object>> jsonEntity(Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    private long idOf(ResponseEntity<Map> response) {
        return ((Number) response.getBody().get("id")).longValue();
    }

    private int listCount() {
        ResponseEntity<Map> response = rest.getForEntity(url("/api/workorders"), Map.class);
        return ((Number) response.getBody().get("count")).intValue();
    }
}
