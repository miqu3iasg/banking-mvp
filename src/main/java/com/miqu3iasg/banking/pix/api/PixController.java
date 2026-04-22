package com.miqu3iasg.banking.pix.api;

import com.miqu3iasg.banking.pix.api.dto.CreatePixChargeRequest;
import com.miqu3iasg.banking.pix.api.dto.PixKeyResponse;
import com.miqu3iasg.banking.pix.api.dto.RegisterPixKeyRequest;
import com.miqu3iasg.banking.pix.gateway.PixChargeResponse;
import com.miqu3iasg.banking.pix.service.PixKeyService;
import com.miqu3iasg.banking.pix.service.PixService;
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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@ConditionalOnProperty(name = "efi.webclient.enabled", havingValue = "true", matchIfMissing = true)
@RestController
@RequestMapping("/pix")
@RequiredArgsConstructor
@Tag(
	name = "PIX",
	description = """
		PIX instant payment operations: key registration and charge management.
		
		**Idempotency**
		Mutating endpoints require an `X-Idempotency-Key` request header.
		Replaying the same key within the idempotency window (24 h) returns the
		original response without re-executing the operation — safe to retry on
		network or timeout failures. Use a UUID v4 per logical operation.
		
		**Related resources**
		- Account details & balance: `GET /accounts/{accountId}`
		- Full transaction history:  `GET /accounts/{accountId}/transactions`
		"""
)
@SecurityRequirement(name = "bearerAuth")
public class PixController {

	/**
	 * Request header that carries the client-generated idempotency key.
	 * UUID v4 is the recommended format (e.g. {@code "f47ac10b-58cc-4372-a567-0e02b2c3d479"}).
	 */
	static final String IDEMPOTENCY_KEY_HEADER = "X-Idempotency-Key";

	private static final String EXAMPLE_IDEMPOTENCY_KEY = "f47ac10b-58cc-4372-a567-0e02b2c3d479";

	private static final String EXAMPLE_CHARGE_RESPONSE = """
		{
		  "txid":      "A1B2C3D4E5F6G7H8I9J0K1L2M3",
		  "accountId": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
		  "amount":    150.00,
		  "status":    "PENDING",
		  "copyPaste": "00020101021226870014br.gov.bcb.pix...",
		  "expiresAt": "2024-06-15T11:30:00Z",
		  "createdAt": "2024-06-15T11:00:00Z"
		}
		""";

	private static final String EXAMPLE_KEY_RESPONSE = """
		{
		  "id":        "3fa85f64-5717-4562-b3fc-2c963f66afa6",
		  "accountId": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
		  "keyType":   "CPF",
		  "keyValue":  "123.456.789-00",
		  "status":    "ACTIVE",
		  "createdAt": "2024-06-15T10:00:00Z",
		  "updatedAt": "2024-06-15T10:00:00Z"
		}
		""";

	private static final String EXAMPLE_400 = """
		{
		  "type":     "https://banking.example.com/problems/validation-error",
		  "title":    "Validation Failed",
		  "status":   400,
		  "detail":   "amount: must be greater than 0",
		  "instance": "/pix/charges"
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
		  "type":     "https://banking.example.com/problems/charge-not-found",
		  "title":    "PIX Charge Not Found",
		  "status":   404,
		  "detail":   "No PIX charge found with txid 'A1B2C3D4E5F6G7H8I9J0K1L2M3'.",
		  "instance": "/pix/charges/A1B2C3D4E5F6G7H8I9J0K1L2M3"
		}
		""";

	private static final String EXAMPLE_409 = """
		{
		  "type":     "https://banking.example.com/problems/idempotency-conflict",
		  "title":    "Idempotency Key Conflict",
		  "status":   409,
		  "detail":   "Key 'f47ac10b-...' was previously used for a different request payload.",
		  "instance": "/pix/charges"
		}
		""";

	private static final String EXAMPLE_422_NO_KEY = """
		{
		  "type":     "https://banking.example.com/problems/pix-key-not-found",
		  "title":    "No Active PIX Key",
		  "status":   422,
		  "detail":   "No active PIX key found for account '9b1deb4d-...'. Register a PIX key before creating charges.",
		  "instance": "/pix/charges"
		}
		""";

	private static final String EXAMPLE_422_INVALID_STATE = """
		{
		  "type":     "https://banking.example.com/problems/invalid-pix-state-transition",
		  "title":    "Invalid PIX State Transition",
		  "status":   422,
		  "detail":   "Cannot cancel a PIX charge with status PAID.",
		  "instance": "/pix/charges/A1B2C3D4E5F6G7H8I9J0K1L2M3/cancel"
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

	private final PixService pixService;
	private final PixKeyService pixKeyService;

	@PostMapping("/accounts/{accountId}/charges")
	@Operation(
		summary = "Create a PIX charge (QR Code)",
		description = """
			Generates a PIX instant-payment charge linked to an active PIX key of the account.
			
			**Flow**
			1. An active PIX key must exist for the account — the first one found is used.
			2. A charge is persisted locally with status `PENDING`.
			3. The charge is registered at Efí Bank via the `PUT /v2/cob/{txid}` endpoint.
			4. The `copyPaste` (Copia e Cola) string and `qrCode` are stored and returned.
			
			**Idempotency**
			Replaying the same `X-Idempotency-Key` within 24 h returns the original
			`201` response without re-registering the charge at Efí Bank.
			
			**Payment notification**
			When the payer settles the charge, Efí Bank delivers a webhook to
			`POST /v1/pix/webhook/pix`. The charge status transitions to `PAID` automatically.
			
			**Related endpoints**
			- Retrieve this charge:       `GET /pix/accounts/{accountId}/charges/{txid}`
			- Cancel pending charge:      `DELETE /pix/accounts/{accountId}/charges/{txid}`
			- Register a PIX key:         `POST /pix/accounts/{accountId}/keys`
			"""
	)
	@ApiResponses({
		@ApiResponse(
			responseCode = "201",
			description = "PIX charge created successfully. "
				+ "The `Location` header points to the created charge resource.",
			headers = @Header(
				name = "Location",
				description = "URI of the newly created charge, e.g. `/pix/accounts/{accountId}/charges/{txid}`",
				schema = @Schema(type = "string", format = "uri")
			),
			content = @Content(
				mediaType = "application/json",
				schema = @Schema(implementation = PixChargeResponse.class),
				examples = @ExampleObject(name = "Successful charge creation", value = EXAMPLE_CHARGE_RESPONSE)
			)
		),
		@ApiResponse(
			responseCode = "400",
			description = """
				Validation failure. Common causes:
				- `amount` is missing, zero, or negative
				- `amount` has more than 2 decimal places
				- `payerName` exceeds 200 characters
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
				- Account has no active PIX key — register one first via `POST /pix/accounts/{accountId}/keys`
				""",
			content = @Content(
				mediaType = "application/problem+json",
				schema = @Schema(implementation = ProblemDetail.class),
				examples = @ExampleObject(name = "No active PIX key", value = EXAMPLE_422_NO_KEY)
			)
		)
	})
	public ResponseEntity<PixChargeResponse> createCharge (
		@Parameter(description = "Account UUID that owns the charge", required = true)
		@PathVariable UUID accountId,

		@Parameter(description = IDEMPOTENCY_KEY_DESCRIPTION, required = true, example = EXAMPLE_IDEMPOTENCY_KEY)
		@RequestHeader(IDEMPOTENCY_KEY_HEADER)
		@NotBlank @Size(max = 100)
		String idempotencyKey,

		@Valid @RequestBody CreatePixChargeRequest request
	) {
		PixChargeResponse response = pixService.createCharge(accountId, request, idempotencyKey);

		return ResponseEntity
			.created(URI.create("/pix/accounts/" + accountId + "/charges/" + response.txid()))
			.body(response);
	}

	@GetMapping("/accounts/{accountId}/charges/{txid}")
	@Operation(
		summary = "Retrieve a PIX charge",
		description = """
			Returns the current state of a PIX charge, including status, `copyPaste` string,
			and payment timestamp if already settled.
			"""
	)
	@ApiResponses({
		@ApiResponse(
			responseCode = "200",
			description = "PIX charge found.",
			content = @Content(
				mediaType = "application/json",
				schema = @Schema(implementation = PixChargeResponse.class),
				examples = @ExampleObject(name = "Charge details", value = EXAMPLE_CHARGE_RESPONSE)
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
			description = "No charge found for the supplied `txid` under this account.",
			content = @Content(
				mediaType = "application/problem+json",
				schema = @Schema(implementation = ProblemDetail.class),
				examples = @ExampleObject(name = "Charge not found", value = EXAMPLE_404)
			)
		)
	})
	public ResponseEntity<PixChargeResponse> getCharge (
		@Parameter(description = "Account UUID that owns the charge", required = true)
		@PathVariable UUID accountId,

		@Parameter(description = "PIX transaction ID (txid) — 26 to 35 alphanumeric characters", required = true)
		@PathVariable String txid
	) {
		return ResponseEntity.ok(pixService.getCharge(txid, accountId));
	}

	@DeleteMapping("/accounts/{accountId}/charges/{txid}")
	@Operation(
		summary = "Cancel a pending PIX charge",
		description = """
			Cancels a PIX charge that is still in `PENDING` status.
			The cancellation is propagated to Efí Bank. Charges that are already
			`PAID`, `CANCELLED`, or `EXPIRED` cannot be cancelled.
			"""
	)
	@ApiResponses({
		@ApiResponse(
			responseCode = "204",
			description = "PIX charge cancelled successfully. No response body."
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
			description = "No charge found for the supplied `txid` under this account.",
			content = @Content(
				mediaType = "application/problem+json",
				schema = @Schema(implementation = ProblemDetail.class),
				examples = @ExampleObject(name = "Charge not found", value = EXAMPLE_404)
			)
		),
		@ApiResponse(
			responseCode = "422",
			description = "Charge is not in a cancellable state (e.g. already `PAID` or `EXPIRED`).",
			content = @Content(
				mediaType = "application/problem+json",
				schema = @Schema(implementation = ProblemDetail.class),
				examples = @ExampleObject(name = "Invalid state transition", value = EXAMPLE_422_INVALID_STATE)
			)
		)
	})
	public ResponseEntity<Void> cancelCharge (
		@Parameter(description = "Account UUID that owns the charge", required = true)
		@PathVariable UUID accountId,

		@Parameter(description = "PIX transaction ID (txid) to cancel", required = true)
		@PathVariable String txid
	) {
		pixService.cancelCharge(txid, accountId);

		return ResponseEntity.noContent().build();
	}

	@PostMapping("/accounts/{accountId}/keys")
	@Operation(
		summary = "Register a PIX key",
		description = """
			Registers a new PIX key (CPF, CNPJ, email, phone, or EVP) for the account.
			The key is validated and registered with the PSP before being stored locally.
			"""
	)
	@ApiResponses({
		@ApiResponse(
			responseCode = "201",
			description = "PIX key registered successfully.",
			headers = @Header(
				name = "Location",
				description = "URI of the newly registered key, e.g. `/pix/accounts/{accountId}/keys/{keyId}`",
				schema = @Schema(type = "string", format = "uri")
			),
			content = @Content(
				mediaType = "application/json",
				schema = @Schema(implementation = PixKeyResponse.class),
				examples = @ExampleObject(name = "Registered PIX key", value = EXAMPLE_KEY_RESPONSE)
			)
		),
		@ApiResponse(
			responseCode = "400",
			description = "Validation failure — missing or invalid `keyType` / `keyValue`.",
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
		)
	})
	public ResponseEntity<PixKeyResponse> registerKey (
		@Parameter(description = "Account UUID to register the key under", required = true)
		@PathVariable UUID accountId,

		@Valid @RequestBody RegisterPixKeyRequest request
	) {
		PixKeyResponse response = pixKeyService.registerKey(accountId, request);

		return ResponseEntity
			.created(URI.create("/pix/accounts/" + accountId + "/keys/" + response.id()))
			.body(response);
	}

	@GetMapping("/accounts/{accountId}/keys")
	@Operation(
		summary = "List PIX keys for an account",
		description = "Returns all PIX keys registered under the account, regardless of status."
	)
	@ApiResponses({
		@ApiResponse(
			responseCode = "200",
			description = "PIX keys retrieved successfully.",
			content = @Content(
				mediaType = "application/json",
				schema = @Schema(implementation = PixKeyResponse.class),
				examples = @ExampleObject(name = "PIX key list", value = "[" + EXAMPLE_KEY_RESPONSE + "]")
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
		)
	})
	public ResponseEntity<List<PixKeyResponse>> listKeys (
		@Parameter(description = "Account UUID", required = true)
		@PathVariable UUID accountId
	) {
		return ResponseEntity.ok(pixKeyService.listKeys(accountId));
	}

	@DeleteMapping("/accounts/{accountId}/keys/{keyId}")
	@Operation(
		summary = "Delete a PIX key",
		description = """
			Removes a PIX key from the account and deregisters it with the PSP.
			Keys linked to pending charges should be cancelled first.
			"""
	)
	@ApiResponses({
		@ApiResponse(
			responseCode = "204",
			description = "PIX key deleted successfully. No response body."
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
			description = "No PIX key found for the supplied `keyId` under this account.",
			content = @Content(
				mediaType = "application/problem+json",
				schema = @Schema(implementation = ProblemDetail.class),
				examples = @ExampleObject(name = "Key not found", value = EXAMPLE_404)
			)
		)
	})
	public ResponseEntity<Void> deleteKey (
		@Parameter(description = "Account UUID that owns the key", required = true)
		@PathVariable UUID accountId,

		@Parameter(description = "PIX key UUID to delete", required = true)
		@PathVariable UUID keyId
	) {
		pixKeyService.deleteKey(accountId, keyId);

		return ResponseEntity.noContent().build();
	}
}
