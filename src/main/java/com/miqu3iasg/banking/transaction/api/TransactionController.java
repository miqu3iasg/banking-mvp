package com.miqu3iasg.banking.transaction.api;

import com.miqu3iasg.banking.transaction.api.dto.TransactionResponse;
import com.miqu3iasg.banking.transaction.api.dto.DepositRequest;
import com.miqu3iasg.banking.transaction.api.dto.TransferRequest;
import com.miqu3iasg.banking.transaction.api.dto.WithdrawalRequest;
import com.miqu3iasg.banking.transaction.service.DepositService;
import com.miqu3iasg.banking.transaction.service.TransferService;
import com.miqu3iasg.banking.transaction.service.WithdrawalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/transactions")
@RequiredArgsConstructor
@Tag(
	name = "Transactions",
	description = """
		Monetary operations: deposit, withdrawal, and internal account-to-account transfer.
		
		**Idempotency**
		Every mutating endpoint requires an `X-Idempotency-Key` request header.
		Replaying the same key within the idempotency window (24 h) returns the
		original response without re-executing the operation — safe to retry on
		network or timeout failures. Use a UUID v4 per logical operation.
		
		**Related resources**
		- Account details & balance: `GET /accounts/{accountId}`
		- Full transaction history: `GET /accounts/{accountId}/transactions`
		- Single transaction lookup: `GET /transactions/{transactionId}`
		"""
)
@SecurityRequirement(name = "bearerAuth")
public class TransactionController {

	/**
	 * Request header that carries the client-generated idempotency key.
	 * UUID v4 is the recommended format (e.g. {@code "f47ac10b-58cc-4372-a567-0e02b2c3d479"}).
	 */
	static final String IDEMPOTENCY_KEY_HEADER = "X-Idempotency-Key";

	private static final String EXAMPLE_IDEMPOTENCY_KEY = "f47ac10b-58cc-4372-a567-0e02b2c3d479";

	private static final String EXAMPLE_TRANSACTION_RESPONSE = """
		{
		  "transactionId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
		  "accountId":     "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
		  "type":          "DEPOSIT",
		  "amount":        500.00,
		  "currency":      "USD",
		  "balanceAfter":  1500.00,
		  "status":        "COMPLETED",
		  "createdAt":     "2024-06-15T10:30:00Z",
		  "_links": {
		    "self":    { "href": "/transactions/3fa85f64-5717-4562-b3fc-2c963f66afa6" },
		    "account": { "href": "/accounts/9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d" }
		  }
		}
		""";

	private static final String EXAMPLE_400 = """
		{
		  "type":     "https://banking.example.com/problems/validation-error",
		  "title":    "Validation Failed",
		  "status":   400,
		  "detail":   "amount: must be greater than 0",
		  "instance": "/transactions/deposit"
		}
		""";

	private static final String EXAMPLE_401 = """
		{
		  "type":   "https://banking.example.com/problems/unauthorized",
		  "title":  "Unauthorized",
		  "status": 401,
		  "detail": "Bearer token is missing or has expired."
		}
		""";

	private static final String EXAMPLE_404 = """
		{
		  "type":     "https://banking.example.com/problems/account-not-found",
		  "title":    "Account Not Found",
		  "status":   404,
		  "detail":   "No account exists with id '9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d'.",
		  "instance": "/transactions/deposit"
		}
		""";

	private static final String EXAMPLE_409 = """
		{
		  "type":     "https://banking.example.com/problems/idempotency-conflict",
		  "title":    "Idempotency Key Conflict",
		  "status":   409,
		  "detail":   "Key 'f47ac10b-...' was previously used for a different request payload.",
		  "instance": "/transactions/deposit"
		}
		""";

	private static final String EXAMPLE_422_BLOCKED = """
		{
		  "type":     "https://banking.example.com/problems/account-not-operable",
		  "title":    "Account Not Operable",
		  "status":   422,
		  "detail":   "Account '9b1deb4d-...' is BLOCKED and cannot receive funds.",
		  "instance": "/transactions/deposit"
		}
		""";

	private static final String EXAMPLE_422_INSUFFICIENT = """
		{
		  "type":     "https://banking.example.com/problems/insufficient-funds",
		  "title":    "Insufficient Funds",
		  "status":   422,
		  "detail":   "Available balance 120.00 USD is less than requested 500.00 USD.",
		  "instance": "/transactions/withdrawal"
		}
		""";

	private static final String IDEMPOTENCY_KEY_DESCRIPTION = """
		Client-generated idempotency key — uniquely identifies this logical operation.
		
		**Format:** UUID v4 is strongly recommended (e.g. `f47ac10b-58cc-4372-a567-0e02b2c3d479`).
		**Max length:** 100 characters.
		**Scope:** Keys are scoped per authenticated user; the same key may be reused
		by different users without conflict.
		**Window:** Duplicate detection is active for 24 hours after the first successful
		request. After expiry the key may be reused to create a new operation.
		**Conflict:** Submitting the same key with a *different* request payload returns
		`409 Conflict`.
		""";

	private final DepositService depositService;
	private final WithdrawalService withdrawalService;
	private final TransferService transferService;

	@PostMapping("/deposit")
	@Operation(
		summary = "Deposit funds into an account",
		description = """
			Credits the specified amount to the target account and records a ledger entry
			of type `DEPOSIT`.
			
			**Flow**
			1. The account is looked up and validated (`ACTIVE` status required).
			2. The amount is credited atomically.
			3. A `TransactionResponse` is returned containing the new balance and a
			   self-link for future retrieval.
			
			**Idempotency**
			Replaying the same `X-Idempotency-Key` within 24 h returns the original
			`201` response without re-crediting the account.
			
			**Related endpoints**
			- Retrieve this transaction later: `GET /transactions/{transactionId}`
			- View updated account balance:    `GET /accounts/{accountId}`
			"""
	)
	@ApiResponses({
		@ApiResponse(
			responseCode = "201",
			description = "Deposit recorded successfully. "
				+ "The `Location` header points to the created transaction resource.",
			headers = @Header(
				name = "Location",
				description = "URI of the newly created transaction, e.g. `/transactions/{transactionId}`",
				schema = @Schema(type = "string", format = "uri")
			),
			content = @Content(
				mediaType = "application/json",
				schema = @Schema(implementation = TransactionResponse.class),
				examples = @ExampleObject(
					name = "Successful deposit",
					value = EXAMPLE_TRANSACTION_RESPONSE
				)
			)
		),
		@ApiResponse(
			responseCode = "400",
			description = """
				Validation failure. Common causes:
				- `amount` is missing, zero, or negative
				- `accountId` is not a valid UUID
				- `currency` code is absent or not ISO-4217
				- `X-Idempotency-Key` header is missing or exceeds 100 characters
				""",
			content = @Content(
				mediaType = "application/problem+json",
				schema = @Schema(implementation = ProblemDetail.class),
				examples = @ExampleObject(name = "Validation error", value = EXAMPLE_400)
			)
		),
		@ApiResponse(
			responseCode = "401",
			description = "Missing or expired Bearer token.",
			content = @Content(
				mediaType = "application/problem+json",
				schema = @Schema(implementation = ProblemDetail.class),
				examples = @ExampleObject(name = "Unauthorized", value = EXAMPLE_401)
			)
		),
		@ApiResponse(
			responseCode = "404",
			description = "No account found for the supplied `accountId`.",
			content = @Content(
				mediaType = "application/problem+json",
				schema = @Schema(implementation = ProblemDetail.class),
				examples = @ExampleObject(name = "Account not found", value = EXAMPLE_404)
			)
		),
		@ApiResponse(
			responseCode = "409",
			description = "The idempotency key was already used with a different request payload.",
			content = @Content(
				mediaType = "application/problem+json",
				schema = @Schema(implementation = ProblemDetail.class),
				examples = @ExampleObject(name = "Idempotency conflict", value = EXAMPLE_409)
			)
		),
		@ApiResponse(
			responseCode = "422",
			description = """
				Business rule violation. Possible reasons:
				- Account status is `BLOCKED` — contact support to unblock
				- Account status is `CLOSED` — deposits are permanently disallowed
				""",
			content = @Content(
				mediaType = "application/problem+json",
				schema = @Schema(implementation = ProblemDetail.class),
				examples = @ExampleObject(name = "Account not operable", value = EXAMPLE_422_BLOCKED)
			)
		)
	})
	public ResponseEntity<TransactionResponse> deposit (
		@Parameter(description = IDEMPOTENCY_KEY_DESCRIPTION, required = true, example = EXAMPLE_IDEMPOTENCY_KEY)
		@RequestHeader(IDEMPOTENCY_KEY_HEADER)
		@NotBlank @Size(max = 100)
		String idempotencyKey,

		@Valid @RequestBody DepositRequest request
	) {
		TransactionResponse response = depositService.deposit(idempotencyKey, request);

		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}

	@PostMapping("/withdrawal")
	@Operation(
		summary = "Withdraw funds from an account",
		description = """
			Debits the specified amount from the account and records a ledger entry
			of type `WITHDRAWAL`.
			
			**Flow**
			1. The account is looked up and validated (`ACTIVE` status required).
			2. Available balance is checked; the request is rejected if insufficient.
			3. The amount is debited atomically.
			4. A `TransactionResponse` is returned containing the remaining balance.
			
			**Idempotency**
			Safe to retry on network failure within 24 h — the account is debited
			only once per unique key.
			
			**Related endpoints**
			- Retrieve this transaction later: `GET /transactions/{transactionId}`
			- View updated account balance:    `GET /accounts/{accountId}`
			"""
	)
	@ApiResponses({
		@ApiResponse(
			responseCode = "201",
			description = "Withdrawal recorded successfully.",
			headers = @Header(
				name = "Location",
				description = "URI of the newly created transaction resource.",
				schema = @Schema(type = "string", format = "uri")
			),
			content = @Content(
				mediaType = "application/json",
				schema = @Schema(implementation = TransactionResponse.class),
				examples = @ExampleObject(
					name = "Successful withdrawal",
					value = """
						{
						  "transactionId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
						  "accountId":     "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
						  "type":          "WITHDRAWAL",
						  "amount":        200.00,
						  "currency":      "USD",
						  "balanceAfter":  800.00,
						  "status":        "COMPLETED",
						  "createdAt":     "2024-06-15T11:00:00Z",
						  "_links": {
						    "self":    { "href": "/transactions/7c9e6679-7425-40de-944b-e07fc1f90ae7" },
						    "account": { "href": "/accounts/9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d" }
						  }
						}
						"""
				)
			)
		),
		@ApiResponse(
			responseCode = "400",
			description = """
				Validation failure. Common causes:
				- `amount` is missing, zero, or negative
				- `accountId` is not a valid UUID
				- `X-Idempotency-Key` header is missing or exceeds 100 characters
				""",
			content = @Content(
				mediaType = "application/problem+json",
				schema = @Schema(implementation = ProblemDetail.class),
				examples = @ExampleObject(name = "Validation error", value = EXAMPLE_400)
			)
		),
		@ApiResponse(
			responseCode = "401",
			description = "Missing or expired Bearer token.",
			content = @Content(
				mediaType = "application/problem+json",
				schema = @Schema(implementation = ProblemDetail.class),
				examples = @ExampleObject(name = "Unauthorized", value = EXAMPLE_401)
			)
		),
		@ApiResponse(
			responseCode = "404",
			description = "No account found for the supplied `accountId`.",
			content = @Content(
				mediaType = "application/problem+json",
				schema = @Schema(implementation = ProblemDetail.class),
				examples = @ExampleObject(name = "Account not found", value = EXAMPLE_404)
			)
		),
		@ApiResponse(
			responseCode = "409",
			description = "The idempotency key was already used with a different request payload.",
			content = @Content(
				mediaType = "application/problem+json",
				schema = @Schema(implementation = ProblemDetail.class),
				examples = @ExampleObject(name = "Idempotency conflict", value = EXAMPLE_409)
			)
		),
		@ApiResponse(
			responseCode = "422",
			description = """
				Business rule violation. Possible reasons:
				- `INSUFFICIENT_FUNDS` — available balance is less than requested amount
				- `ACCOUNT_BLOCKED`    — account is suspended; contact support
				- `ACCOUNT_CLOSED`     — account is permanently closed
				""",
			content = @Content(
				mediaType = "application/problem+json",
				schema = @Schema(implementation = ProblemDetail.class),
				examples = {
					@ExampleObject(name = "Insufficient funds", value = EXAMPLE_422_INSUFFICIENT),
					@ExampleObject(name = "Account not operable", value = EXAMPLE_422_BLOCKED)
				}
			)
		)
	})
	public ResponseEntity<TransactionResponse> withdrawal (
		@Parameter(description = IDEMPOTENCY_KEY_DESCRIPTION, required = true, example = EXAMPLE_IDEMPOTENCY_KEY)
		@RequestHeader(IDEMPOTENCY_KEY_HEADER)
		@NotBlank @Size(max = 100)
		String idempotencyKey,

		@Valid @RequestBody WithdrawalRequest request
	) {
		TransactionResponse response = withdrawalService.withdraw(idempotencyKey, request);

		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}

	@PostMapping("/transfer")
	@Operation(
		summary = "Transfer funds between two accounts",
		description = """
			Atomically debits the origin account and credits the destination account,
			producing two ledger entries: one `TRANSFER_DEBIT` and one `TRANSFER_CREDIT`.
			
			**Flow**
			1. Both accounts are validated (`ACTIVE` status required for each).
			2. Origin and destination must differ (self-transfer returns `400`).
			3. Pessimistic locks are acquired in UUID-ascending order to prevent
			   deadlocks under concurrent requests for the same account pair.
			4. Origin balance is checked; the request is rejected if insufficient.
			5. Both ledger entries are written in the same database transaction.
			6. The response carries the **debit-leg** transaction ID as the canonical
			   reference for the initiating party.
			
			**Idempotency**
			Safe to retry on network failure within 24 h — funds move exactly once
			per unique key.
			
			**Related endpoints**
			- Debit-leg transaction:          `GET /transactions/{transactionId}`
			- Origin account balance:         `GET /accounts/{originAccountId}`
			- Destination account balance:    `GET /accounts/{destinationAccountId}`
			- Full transaction history:       `GET /accounts/{accountId}/transactions`
			"""
	)
	@ApiResponses({
		@ApiResponse(
			responseCode = "201",
			description = """
				Transfer completed successfully. The response body contains the debit-leg
				transaction. The corresponding credit-leg `transactionId` on the destination
				account can be found via `GET /accounts/{destinationAccountId}/transactions`.
				""",
			headers = @Header(
				name = "Location",
				description = "URI of the debit-leg transaction resource.",
				schema = @Schema(type = "string", format = "uri")
			),
			content = @Content(
				mediaType = "application/json",
				schema = @Schema(implementation = TransactionResponse.class),
				examples = @ExampleObject(
					name = "Successful transfer",
					value = """
						{
						  "transactionId":        "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
						  "accountId":            "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
						  "counterpartAccountId": "1a2b3c4d-5e6f-7a8b-9c0d-1e2f3a4b5c6d",
						  "type":                 "TRANSFER_DEBIT",
						  "amount":               300.00,
						  "currency":             "USD",
						  "balanceAfter":         700.00,
						  "status":               "COMPLETED",
						  "createdAt":            "2024-06-15T12:00:00Z",
						  "_links": {
						    "self":        { "href": "/transactions/a1b2c3d4-e5f6-7890-abcd-ef1234567890" },
						    "account":     { "href": "/accounts/9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d" },
						    "counterpart": { "href": "/accounts/1a2b3c4d-5e6f-7a8b-9c0d-1e2f3a4b5c6d" }
						  }
						}
						"""
				)
			)
		),
		@ApiResponse(
			responseCode = "400",
			description = """
				Validation failure. Common causes:
				- `amount` is missing, zero, or negative
				- `originAccountId` or `destinationAccountId` is not a valid UUID
				- `originAccountId` equals `destinationAccountId` (self-transfer)
				- `X-Idempotency-Key` header is missing or exceeds 100 characters
				""",
			content = @Content(
				mediaType = "application/problem+json",
				schema = @Schema(implementation = ProblemDetail.class),
				examples = @ExampleObject(name = "Validation error", value = EXAMPLE_400)
			)
		),
		@ApiResponse(
			responseCode = "401",
			description = "Missing or expired Bearer token.",
			content = @Content(
				mediaType = "application/problem+json",
				schema = @Schema(implementation = ProblemDetail.class),
				examples = @ExampleObject(name = "Unauthorized", value = EXAMPLE_401)
			)
		),
		@ApiResponse(
			responseCode = "404",
			description = "Origin or destination account not found.",
			content = @Content(
				mediaType = "application/problem+json",
				schema = @Schema(implementation = ProblemDetail.class),
				examples = @ExampleObject(name = "Account not found", value = EXAMPLE_404)
			)
		),
		@ApiResponse(
			responseCode = "409",
			description = "The idempotency key was already used with a different request payload.",
			content = @Content(
				mediaType = "application/problem+json",
				schema = @Schema(implementation = ProblemDetail.class),
				examples = @ExampleObject(name = "Idempotency conflict", value = EXAMPLE_409)
			)
		),
		@ApiResponse(
			responseCode = "422",
			description = """
				Business rule violation. Possible reasons:
				- `INSUFFICIENT_FUNDS`         — origin balance is less than requested amount
				- `ORIGIN_ACCOUNT_BLOCKED`      — origin account is suspended
				- `ORIGIN_ACCOUNT_CLOSED`       — origin account is permanently closed
				- `DESTINATION_ACCOUNT_BLOCKED` — destination account is suspended
				- `DESTINATION_ACCOUNT_CLOSED`  — destination account is permanently closed
				""",
			content = @Content(
				mediaType = "application/problem+json",
				schema = @Schema(implementation = ProblemDetail.class),
				examples = {
					@ExampleObject(name = "Insufficient funds", value = EXAMPLE_422_INSUFFICIENT),
					@ExampleObject(name = "Account not operable", value = EXAMPLE_422_BLOCKED)
				}
			)
		)
	})
	public ResponseEntity<TransactionResponse> transfer (
		@Parameter(description = IDEMPOTENCY_KEY_DESCRIPTION, required = true, example = EXAMPLE_IDEMPOTENCY_KEY)
		@RequestHeader(IDEMPOTENCY_KEY_HEADER)
		@NotBlank @Size(max = 100)
		String idempotencyKey,

		@Valid @RequestBody TransferRequest request
	) {
		TransactionResponse response = transferService.transfer(idempotencyKey, request);

		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}
}
