╔══════════════════════════════════════════════════════════════════════════════╗
║   ✅ MODULE AUDIT COMPLETE                        ║
║   Module: shared                                  ║
║   Files audited: 43/43                            ║
╚═══════════════════════════════════════════════════════════════════════════════╝

 ── SNIPPETS CREATED ──────────────────────────────────────────────
 | # | Filename                                              | Tier | Source File(s) | Category           | Description                                    |
 |---|-------------------------------------------------------|------|----------------|--------------------|------------------------------------------------|
 | 1 | audit-dto-pattern-for-tracking-entity-changes.md      | T7   | audit/AccountAuditPayload.java, audit/AccountStatusPayload.java | DTO                | Reusable DTO pattern for capturing entity state changes in audit logs |
 | 2 | audit-action-enum-pattern-for-entity-changes.md       | T5   | audit/AuditAction.java                   | Enum               | Enum pattern for tracking entity audit actions with mapping from domain events |

 ── DUPLICATES SKIPPED ────────────────────────────────────────────
  | # | Intended Snippet             | Existing File                                | Reason                                     |
  |---|------------------------------|----------------------------------------------|--------------------------------------------|
  | 1 | jpa-auditable-base-entity.md | jpa-auditable-base-entity.md                 | Already covered by existing snippet        |
  | 2 | money-value-object.md        | money-value-object-with-currency-arithmetic.md | Already covered by existing snippet        |
  | 3 | postgres-transactional-outbox-pattern.md | postgres-transactional-outbox-pattern.md | Already covered by existing snippet        |

 ── CONFIDENTIAL FILES SKIPPED ───────────────────────────────────
  | # | File Path | Reason |
  |---|-----------|--------|
  |   |           | No confidential files found in module        |

 ── EXTERNAL REFERENCES (out of scope, not analyzed) ─────────────
  | # | Referenced File | Referenced From | Note |
  |---|-----------------|-----------------|------|
  |   |                 |                 | No external references analyzed              |

 ── FILES WITH NO REUSABLE CONTENT ───────────────────────────────
  | # | File Path                                                               | Reason                        |
  |---|---------------------------------------------------------------------------|-------------------------------|
  | 1 | src/main/java/com/miqu3iasg/banking/shared/audit/AuditLogRepository.java  | Simple repository interface   |
  | 2 | src/main/java/com/miqu3iasg/banking/shared/config/EfiProperties.java      | Simple interface              |
  | 3 | src/main/java/com/miqu3iasg/banking/shared/exception/AccountNotFoundException.java | Simple exception class       |
  | 4 | src/main/java/com/miqu3iasg/banking/shared/exception/AccountNumberGenerationException.java | Simple exception class       |
  | 5 | src/main/java/com/miqu3iasg/banking/shared/exception/CurrencyMismatchException.java | Simple exception class       |
  | 6 | src/main/java/com/miqu3iasg/banking/shared/exception/InvalidDocumentException.java | Simple exception class       |
  | 7 | src/main/java/com/miqu3iasg/banking/shared/exception/InvalidRequestException.java | Simple exception class       |
  | 8 | src/main/java/com/miqu3iasg/banking/shared/exception/PurgeInterruptedException.java | Simple exception class       |
  | 9 | src/main/java/com/miqu3iasg/banking/shared/exception/TransientExceptionClassifier.java | Simple utility class         |
  |10 | src/main/java/com/miqu3iasg/banking/shared/exception/metrics/ErrorMetrics.java | Simple metrics class         |
  |11 | src/main/java/com/miqu3iasg/banking/shared/idempotency/IdempotencyKeyRepository.java | Simple repository interface  |
  |12 | src/main/java/com/miqu3iasg/banking/shared/idempotency/IdempotencyKeyStatus.java | Simple enum                  |
  |13 | src/main/java/com/miqu3iasg/banking/shared/idempotency/IdempotencyMetrics.java | Simple metrics class         |
  |14 | src/main/java/com/miqu3iasg/banking/shared/observability/EventListenerMetrics.java | Simple metrics class         |
  |15 | src/main/java/com/miqu3iasg/banking/shared/observability/RetryMetrics.java | Simple metrics class         |
  |16 | src/main/java/com/miqu3iasg/banking/shared/outbox/OutboxEventDispatcher.java | Simple interface             |
  |17 | src/main/java/com/miqu3iasg/banking/shared/outbox/OutboxRepository.java     | Simple repository interface  |
  |18 | src/main/java/com/miqu3iasg/banking/shared/outbox/OutboxStatus.java        | Simple enum                  |

 ── MODULE AUDIT STATISTICS ───────────────────────────────────────
 Module audited:                  shared
 Module path:                     C:\Users\USER\Desktop\projects\active\banking-mvp\banking-mvp\src\main\java\com\miqu3iasg\banking\shared
 Total files in module:           43
 Files read:                      43
 Files skipped (confidential):    0
 External references (skipped):   0
 Snippets created this session:   2
 Duplicates skipped:              3
 Files with no reusable content:  18

 ── SUGGESTED NEXT MODULE ─────────────────────────────────────────
 If this project has additional modules, consider auditing next:
   - com.miqu3iasg.banking.account (referenced in shared exceptions and services)
   - com.miqu3iasg.banking.pix (referenced in shared config and exceptions)
   - com.miqu3iasg.banking.boleto (referenced in shared config and exceptions)
   - com.miqu3iasg.banking.transaction (referenced in shared audit and idempotency)
