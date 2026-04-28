package com.miqu3iasg.banking.transaction.api;

import com.miqu3iasg.banking.transaction.api.dto.TransactionResponse;
import com.miqu3iasg.banking.transaction.domain.TransactionType;
import com.miqu3iasg.banking.transaction.service.TransactionHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Transaction History", description = "Query transaction history for an account")
@SecurityRequirement(name = "bearerAuth")
public class TransactionHistoryController {

    private static final String EXAMPLE_404 = """
            {
              "type": "https://banking.example.com/problems/account-not-found",
              "title": "Account Not Found",
              "status": 404,
              "detail": "No account exists with id '9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d'.",
              "instance": "/accounts/9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d/transactions"
            }
            """;

    private final TransactionHistoryService transactionHistoryService;

    @GetMapping("/accounts/{accountId}/transactions")
    @Operation(
            summary = "List account transaction history",
            description = """
                    Returns a paginated list of transactions for the specified account,
                    ordered by creation date descending.

                    **Filtering**
                    - `from` / `to` — ISO-8601 instant date range (inclusive)
                    - `type` — filter by transaction type (CREDIT, DEBIT, TRANSFER_DEBIT, TRANSFER_CREDIT)

                    **Pagination**
                    - `page` — zero-based page number (default 0)
                    - `size` — page size (default 20, max 100)
                    """
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Paginated transaction list returned successfully."
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "No account found for the supplied accountId.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class),
                            examples = @ExampleObject(name = "Account not found", value = EXAMPLE_404)
                    )
            )
    })
    public ResponseEntity<Page<TransactionResponse>> getTransactionHistory(
            @Parameter(description = "UUID of the account", required = true, example = "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d")
            @PathVariable UUID accountId,
            @Parameter(description = "Start of date range (ISO-8601 instant, inclusive)")
            @RequestParam(required = false) Instant from,
            @Parameter(description = "End of date range (ISO-8601 instant, inclusive)")
            @RequestParam(required = false) Instant to,
            @Parameter(description = "Filter by transaction type")
            @RequestParam(required = false) TransactionType type,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<TransactionResponse> page = transactionHistoryService.findTransactions(accountId, from, to, type, pageable);
        return ResponseEntity.ok(page);
    }
}
