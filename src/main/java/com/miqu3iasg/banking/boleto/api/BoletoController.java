package com.miqu3iasg.banking.boleto.api;

import com.miqu3iasg.banking.boleto.api.dto.IssueBoletoRequest;
import com.miqu3iasg.banking.boleto.api.dto.IssueBoletoResponse;
import com.miqu3iasg.banking.boleto.service.BoletoService;
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
import java.util.UUID;

@ConditionalOnProperty(name = "efi.webclient.enabled", havingValue = "true", matchIfMissing = true)
@RestController
@RequestMapping("/boletos")
@RequiredArgsConstructor
@Tag(
	name = "Boletos",
	description = """
		Boleto issuance and payment operations.
		
		A boleto is a Brazilian payment slip that can be issued for a customer to pay
		via bank transfer or in-person at a bank branch. Once paid, the amount is
		credited to the recipient account.
		
		**Idempotency**
		Every mutating endpoint requires an `X-Idempotency-Key` request header.
		Replaying the same key within the idempotency window (24 h) returns the
		original response without re-executing the operation.
		
		**Related resources**
		- Account details & balance: `GET /accounts/{accountId}`
		- Transaction history: `GET /accounts/{accountId}/transactions`
		"""
)
@SecurityRequirement(name = "bearerAuth")
public class BoletoController {

	static final String IDEMPOTENCY_KEY_HEADER = "X-Idempotency-Key";

	private static final String EXAMPLE_IDEMPOTENCY_KEY = "f47ac10b-58cc-4372-a567-0e02b2c3d479";

	private static final String EXAMPLE_BOLETO_RESPONSE = """
		{
		  "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
		  "providerChargeId": 12345678,
		  "payerName": "John Doe",
		  "payerDocument": "12345678901",
		  "amount": 500.00,
		  "dueDate": "2024-12-31",
		  "description": "Invoice #12345",
		  "barcode": "00000.00000 00000.000000 00000.000000 0 00000000000000",
		  "billetLink": "https://boleto.example.com/12345678",
		  "pdfUrl": "https://boleto.example.com/pdf/12345678",
		  "status": "PENDING"
		}
		""";

	private static final String EXAMPLE_400 = """
		{
		  "type": "https://banking.example.com/problems/validation-error",
		  "title": "Validation Failed",
		  "status": 400,
		  "detail": "amount: must be greater than 0",
		  "instance": "/boletos"
		}
		""";

	private static final String EXAMPLE_401 = """
		{
		  "type": "https://banking.example.com/problems/unauthorized",
		  "title": "Unauthorized",
		  "status": 401,
		  "detail": "Bearer token is missing or has expired."
		}
		""";

	private static final String EXAMPLE_404 = """
		{
		  "type": "https://banking.example.com/problems/boleto-not-found",
		  "title": "Boleto Not Found",
		  "status": 404,
		  "detail": "No boleto exists with id '3fa85f64-5717-4562-b3fc-2c963f66afa6'.",
		  "instance": "/boletos/3fa85f64-5717-4562-b3fc-2c963f66afa6"
		}
		""";

	private static final String EXAMPLE_422_BLOCKED = """
		{
		  "type": "https://banking.example.com/problems/account-not-operable",
		  "title": "Account Not Operable",
		  "status": 422,
		  "detail": "Account '9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d' is BLOCKED and cannot receive funds.",
		  "instance": "/boletos"
		}
		""";

	private static final String IDEMPOTENCY_KEY_DESCRIPTION = """
		Client-generated idempotency key — uniquely identifies this logical operation.
		
		**Format:** UUID v4 is strongly recommended (e.g. `f47ac10b-58cc-4372-a567-0e02b2c3d479`).
		**Max length:** 100 characters.
		**Scope:** Keys are scoped per authenticated user.
		**Window:** Duplicate detection is active for 24 hours after the first successful
		request.
		""";

	private final BoletoService boletoService;

	@PostMapping
	@Operation(
		summary = "Issue a new boleto",
		description = """
			Creates a new boleto (payment slip) for a customer to pay.
			
			**Flow**
			1. The account is looked up and validated (`ACTIVE` status required).
			2. The boleto is issued via the payment provider (Efí Bank).
			3. Provider data (barcode, billet link, PDF URL) is attached.
			4. The boleto is persisted in `PENDING` status.
			5. An `IssueBoletoResponse` is returned with all necessary payment information.
			
			**Idempotency**
			Replaying the same `X-Idempotency-Key` within 24 h returns the original
			`201` response without re-issuing the boleto.
			
			**Related endpoints**
			- Retrieve this boleto later: `GET /boletos/{boletoId}`
			- View account balance: `GET /accounts/{accountId}`
			"""
	)
	@ApiResponses({
		@ApiResponse(
			responseCode = "201",
			description = "Boleto issued successfully. "
				+ "The `Location` header points to the created boleto resource.",
			headers = @Header(
				name = "Location",
				description = "URI of the newly created boleto, e.g. `/boletos/{boletoId}`",
				schema = @Schema(type = "string", format = "uri")
			),
			content = @Content(
				mediaType = "application/json",
				schema = @Schema(implementation = IssueBoletoResponse.class),
				examples = @ExampleObject(
					name = "Successful boleto issuance",
					value = EXAMPLE_BOLETO_RESPONSE
				)
			)
		),
		@ApiResponse(
			responseCode = "400",
			description = """
				Validation failure. Common causes:
				- `amount` is missing, zero, or negative
				- `accountId` is not a valid UUID
				- `payerName` is blank
				- `payerDocument` is blank or invalid
				- `dueDate` is missing or not in the future
				- `description` is blank or exceeds 255 characters
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
			responseCode = "422",
			description = """
				Business rule violation. Possible reasons:
				- Account status is `BLOCKED` — contact support to unblock
				- Account status is `CLOSED` — cannot receive funds
				""",
			content = @Content(
				mediaType = "application/problem+json",
				schema = @Schema(implementation = ProblemDetail.class),
				examples = @ExampleObject(name = "Account not operable", value = EXAMPLE_422_BLOCKED)
			)
		)
	})
	public ResponseEntity<IssueBoletoResponse> issue (
		@Parameter(description = IDEMPOTENCY_KEY_DESCRIPTION, required = true, example = EXAMPLE_IDEMPOTENCY_KEY)
		@RequestHeader(IDEMPOTENCY_KEY_HEADER)
		@NotBlank @Size(max = 100)
		String idempotencyKey,

		@Valid @RequestBody IssueBoletoRequest request
	) {
		IssueBoletoResponse response = boletoService.issue(
			request.accountId(),
			request,
			idempotencyKey
		);

		URI location = URI.create("/boletos/" + response.id());

		return ResponseEntity.created(location).body(response);
	}

	@GetMapping("/{boletoId}")
	@Operation(
		summary = "Retrieve boleto details",
		description = """
			Returns the current state of a boleto including its status, payment information,
			and HATEOAS links.
			
			**Related endpoints**
			- Issue a new boleto: `POST /boletos`
			- Account details: `GET /accounts/{accountId}`
			"""
	)
	@ApiResponses({
		@ApiResponse(
			responseCode = "200",
			description = "Boleto found and returned successfully.",
			content = @Content(
				mediaType = "application/json",
				schema = @Schema(implementation = IssueBoletoResponse.class),
				examples = @ExampleObject(
					name = "Boleto details",
					value = EXAMPLE_BOLETO_RESPONSE
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
			description = "No boleto found for the supplied `boletoId`.",
			content = @Content(
				mediaType = "application/problem+json",
				schema = @Schema(implementation = ProblemDetail.class),
				examples = @ExampleObject(name = "Boleto not found", value = EXAMPLE_404)
			)
		)
	})
	public ResponseEntity<IssueBoletoResponse> findById (
		@Parameter(description = "UUID of the boleto to retrieve.", required = true, example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
		@PathVariable UUID boletoId
	) {
		IssueBoletoResponse response = IssueBoletoResponse.from(
			boletoService.findById(boletoId)
		);

		return ResponseEntity.ok(response);
	}
}
