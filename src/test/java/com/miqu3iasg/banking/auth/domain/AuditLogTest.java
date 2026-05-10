package com.miqu3iasg.banking.auth.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AuditLogTest {

    @Test
    void builder_createsValidAuditLog() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        AuditLog log = AuditLog.builder()
                .id(id)
                .userId(userId)
                .eventType("LOGIN_SUCCESS")
                .outcome("SUCCESS")
                .ipHash("iphash123")
                .userAgentHash("uahash123")
                .traceId(UUID.randomUUID().toString())
                .build();

        assertEquals(id, log.getId());
        assertEquals(userId, log.getUserId());
        assertEquals("LOGIN_SUCCESS", log.getEventType());
        assertEquals("SUCCESS", log.getOutcome());
    }

    @Test
    void eventType_returnsCorrectValue() {
        AuditLog log = AuditLog.builder().eventType("PASSWORD_CHANGE").build();
        assertEquals("PASSWORD_CHANGE", log.getEventType());
    }

    @Test
    void outcome_returnsCorrectValue() {
        AuditLog log = AuditLog.builder().outcome("FAILURE").build();
        assertEquals("FAILURE", log.getOutcome());
    }

    @Test
    void traceId_returnsCorrectValue() {
        String traceId = UUID.randomUUID().toString();
        AuditLog log = AuditLog.builder().traceId(traceId).build();
        assertEquals(traceId, log.getTraceId());
    }
}
