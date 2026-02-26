package com.miqu3iasg.banking.account.api;

import com.miqu3iasg.banking.account.api.dto.AccountResponse;
import com.miqu3iasg.banking.account.api.dto.CreateAccountRequest;
import com.miqu3iasg.banking.account.domain.AccountAction;
import com.miqu3iasg.banking.account.service.AccountService;
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
import lombok.RequiredArgsConstructor;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/accounts")
@RequiredArgsConstructor
@Tag(
	name = "Accounts",
	description = """
		Account lifecycle operations: open, lookup, and status management (block, unblock, close).
		
		**Related resources**
		- Transaction operations: `POST /transactions/deposit`, `POST /transactions/withdrawal`, `POST /transactions/transfer`
		- Full transaction history: `GET /accounts/{accountId}/transactions`
		- Single transaction lookup: `GET /transactions/{transactionId}`
		"""
)
@SecurityRequirement(name = "bearerAuth")
public class AccountController {

	private static final String EXAMPLE_ACCOUNT_RESPONSE = """
		{
		  "accountId":     "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
		  "accountNumber": "0001-2345-6789",
		  "type":          "CHECKING",
		  "holderName":    "Jane Doe",
		  "email":         "jane.doe@example.com",
		  "status":        "ACTIVE",
		  "balance":       1500.00,
		  "currency":      "USD",
		  "createdAt":     "2024-06-15T09:00:00Z",
		  "_links": {
		    "self":         { "href": "/accounts/9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d" },
		    "transactions": { "href": "/accounts/9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d/transactions" }
		  }
		}
		""";

	private static final String EXAMPLE_400 = """
		{
		  "type":     "https://banking.example.com/problems/validation-error",
		  "title":    "Validation Failed",
		  "status":   400,
		  "detail":   "holderName: must not be blank",
		  "instance": "/accounts"
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
		  "instance": "/accounts/9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d"
		}
		""";

	private static final String EXAMPLE_409 = """
		{
		  "type":     "https://banking.example.com/problems/account-already-exists",
		  "title":    "Account Already Exists",
		  "status":   409,
		  "detail":   "An account for document '***456789' already exists.",
		  "instance": "/accounts"
		}
		""";

	private static final String EXAMPLE_422_TRANSITION = """
		{
		  "type":     "https://banking.example.com/problems/invalid-status-transition",
		  "title":    "Invalid Status Transition",
		  "status":   422,
		  "detail":   "Cannot apply action UNBLOCK to an account with status ACTIVE.",
		  "instance": "/accounts/9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d/block"
		}
		""";

	private static final String EXAMPLE_422_CLOSED = """
		{
		  "type":     "https://banking.example.com/problems/account-not-operable",
		  "title":    "Account Not Operable",
		  "status":   422,
		  "detail":   "Account '9b1deb4d-...' is CLOSED and cannot be modified.",
		  "instance": "/accounts/9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d/block"
		}
		""";

	private final AccountService accountService;

	@PostMapping
	@Operation(
		summary = "Open a new bank account",
		description = """
			Creates a new bank account for a customer and records an initial ledger state.
			
			**Flow**
			1. The request payload is validated (holder name, email, document number, account type).
			2. Duplicate document numbers are rejected — one account per document.
			3. A unique account number is generated and the account is persisted in `ACTIVE` status.
			4. An `AccountResponse` is returned containing the new account ID and a self-link.
			
			**Related endpoints**
			- Retrieve this account later: `GET /accounts/{accountId}`
			- Make a first deposit:        `POST /transactions/deposit`
			"""
	)
	@ApiResponses({
		@ApiResponse(
			responseCode = "201",
			description = "Account opened successfully. "
				+ "The `Location` header points to the created account resource.",
			headers = @Header(
				name = "Location",
				description = "URI of the newly created account, e.g. `/accounts/{accountId}`",
				schema = @Schema(type = "string", format = "uri")
			),
			content = @Content(
				mediaType = "application/json",
				schema = @Schema(implementation = AccountResponse.class),
				examples = @ExampleObject(
					name = "Successful account opening",
					value = EXAMPLE_ACCOUNT_RESPONSE
				)
			)
		),
		@ApiResponse(
			responseCode = "400",
			description = """
				Validation failure. Common causes:
				- `holderName` is missing or blank
				- `email` is missing, blank, or not a valid e-mail address
				- `documentNumber` is missing, blank, or fails format validation
				- `type` is null or not a recognised account type
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
			responseCode = "409",
			description = "An account already exists for the supplied `documentNumber`.",
			content = @Content(
				mediaType = "application/problem+json",
				schema = @Schema(implementation = ProblemDetail.class),
				examples = @ExampleObject(name = "Account already exists", value = EXAMPLE_409)
			)
		)
	})
	public ResponseEntity<AccountResponse> openAccount (
		@Valid @RequestBody CreateAccountRequest request
	) {
		AccountResponse response = accountService.openAccount(request);

		URI location = URI.create("/accounts/" + response.id());

		return ResponseEntity.created(location).body(response);
	}

	@GetMapping("/{accountId}")
	@Operation(
		summary = "Retrieve account details",
		description = """
			Returns the current state of an account including its balance, status, holder
			information, and HATEOAS links.
			
			**Related endpoints**
			- Transaction history: `GET /accounts/{accountId}/transactions`
			- Deposit funds:       `POST /transactions/deposit`
			"""
	)
	@ApiResponses({
		@ApiResponse(
			responseCode = "200",
			description = "Account found and returned successfully.",
			content = @Content(
				mediaType = "application/json",
				schema = @Schema(implementation = AccountResponse.class),
				examples = @ExampleObject(
					name = "Account details",
					value = EXAMPLE_ACCOUNT_RESPONSE
				)
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
		)
	})
	public ResponseEntity<AccountResponse> findById (
		@Parameter(description = "UUID of the account to retrieve.", required = true, example = "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d")
		@PathVariable UUID accountId
	) {
		AccountResponse response = accountService.findById(accountId);

		return ResponseEntity.ok(response);
	}

	@PostMapping("/{accountId}/block")
	@Operation(
		summary = "Block an account",
		description = """
			Transitions the account to `BLOCKED` status, preventing any further deposits,
			withdrawals, or transfers until it is unblocked.
			
			**Flow**
			1. The account is looked up; `404` is returned if not found.
			2. The state transition `ACTIVE → BLOCKED` is validated.
			3. The status is persisted; optimistic-lock conflicts are retried automatically
			   (up to 3 attempts with exponential back-off).
			
			**Related endpoints**
			- Unblock this account: `POST /accounts/{accountId}/unblock`
			- Close this account:   `POST /accounts/{accountId}/close`
			"""
	)
	@ApiResponses({
		@ApiResponse(
			responseCode = "200",
			description = "Account blocked successfully.",
			content = @Content(
				mediaType = "application/json",
				schema = @Schema(implementation = AccountResponse.class),
				examples = @ExampleObject(
					name = "Blocked account",
					value = """
						{
						  "accountId":     "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
						  "accountNumber": "0001-2345-6789",
						  "type":          "CHECKING",
						  "holderName":    "Jane Doe",
						  "email":         "jane.doe@example.com",
						  "status":        "BLOCKED",
						  "balance":       1500.00,
						  "currency":      "USD",
						  "createdAt":     "2024-06-15T09:00:00Z",
						  "_links": {
						    "self":         { "href": "/accounts/9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d" },
						    "transactions": { "href": "/accounts/9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d/transactions" }
						  }
						}
						"""
				)
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
			responseCode = "422",
			description = """
				Business rule violation. Possible reasons:
				- Account is already `BLOCKED`
				- Account is `CLOSED` — terminal state, no transitions allowed
				""",
			content = @Content(
				mediaType = "application/problem+json",
				schema = @Schema(implementation = ProblemDetail.class),
				examples = {
					@ExampleObject(name = "Invalid status transition", value = EXAMPLE_422_TRANSITION),
					@ExampleObject(name = "Account closed", value = EXAMPLE_422_CLOSED)
				}
			)
		)
	})
	public ResponseEntity<AccountResponse> blockAccount (
		@Parameter(description = "UUID of the account to block.", required = true, example = "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d")
		@PathVariable UUID accountId
	) {
		AccountResponse response = accountService.applyStatusAction(accountId, AccountAction.BLOCK_ACCOUNT_USAGE);

		return ResponseEntity.ok(response);
	}

	@PostMapping("/{accountId}/unblock")
	@Operation(
		summary = "Unblock an account",
		description = """
			Transitions the account from `BLOCKED` back to `ACTIVE` status, restoring full
			transaction capabilities.
			
			**Flow**
			1. The account is looked up; `404` is returned if not found.
			2. The state transition `BLOCKED → ACTIVE` is validated.
			3. The status is persisted; optimistic-lock conflicts are retried automatically.
			
			**Related endpoints**
			- Block this account:  `POST /accounts/{accountId}/block`
			- Close this account:  `POST /accounts/{accountId}/close`
			"""
	)
	@ApiResponses({
		@ApiResponse(
			responseCode = "200",
			description = "Account unblocked successfully.",
			content = @Content(
				mediaType = "application/json",
				schema = @Schema(implementation = AccountResponse.class),
				examples = @ExampleObject(
					name = "Unblocked account",
					value = EXAMPLE_ACCOUNT_RESPONSE
				)
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
			responseCode = "422",
			description = """
				Business rule violation. Possible reasons:
				- Account is already `ACTIVE` — it was never blocked
				- Account is `CLOSED` — terminal state, no transitions allowed
				""",
			content = @Content(
				mediaType = "application/problem+json",
				schema = @Schema(implementation = ProblemDetail.class),
				examples = {
					@ExampleObject(name = "Invalid status transition", value = EXAMPLE_422_TRANSITION),
					@ExampleObject(name = "Account closed", value = EXAMPLE_422_CLOSED)
				}
			)
		)
	})
	public ResponseEntity<AccountResponse> unblockAccount (
		@Parameter(description = "UUID of the account to unblock.", required = true, example = "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d")
		@PathVariable UUID accountId
	) {
		AccountResponse response = accountService.applyStatusAction(accountId, AccountAction.UNBLOCK_ACCOUNT_USAGE);

		return ResponseEntity.ok(response);
	}

	@PostMapping("/{accountId}/close")
	@Operation(
		summary = "Close an account",
		description = """
			Permanently transitions the account to `CLOSED` status. This is a terminal
			state — a closed account cannot be reopened, blocked, or unblocked.
			
			**Flow**
			1. The account is looked up; `404` is returned if not found.
			2. The state transition `ACTIVE | BLOCKED → CLOSED` is validated.
			3. The status is persisted; optimistic-lock conflicts are retried automatically.
			
			**Warning:** This action is irreversible. Ensure any remaining balance has
			been withdrawn before closing.
			
			**Related endpoints**
			- Retrieve account balance before closing: `GET /accounts/{accountId}`
			- Withdraw remaining balance:              `POST /transactions/withdrawal`
			"""
	)
	@ApiResponses({
		@ApiResponse(
			responseCode = "200",
			description = "Account closed successfully.",
			content = @Content(
				mediaType = "application/json",
				schema = @Schema(implementation = AccountResponse.class),
				examples = @ExampleObject(
					name = "Closed account",
					value = """
						{
						  "accountId":     "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
						  "accountNumber": "0001-2345-6789",
						  "type":          "CHECKING",
						  "holderName":    "Jane Doe",
						  "email":         "jane.doe@example.com",
						  "status":        "CLOSED",
						  "balance":       0.00,
						  "currency":      "USD",
						  "createdAt":     "2024-06-15T09:00:00Z",
						  "_links": {
						    "self":         { "href": "/accounts/9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d" },
						    "transactions": { "href": "/accounts/9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d/transactions" }
						  }
						}
						"""
				)
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
			responseCode = "422",
			description = "Account is already `CLOSED` — it cannot be closed again.",
			content = @Content(
				mediaType = "application/problem+json",
				schema = @Schema(implementation = ProblemDetail.class),
				examples = @ExampleObject(name = "Account closed", value = EXAMPLE_422_CLOSED)
			)
		)
	})
	public ResponseEntity<AccountResponse> closeAccount (
		@Parameter(description = "UUID of the account to close.", required = true, example = "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d")
		@PathVariable UUID accountId
	) {
		AccountResponse response = accountService.applyStatusAction(accountId, AccountAction.CLOSE_ACCOUNT);

		return ResponseEntity.ok(response);
	}
}
