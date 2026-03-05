package com.miqu3iasg.banking_mvp.efi.pix.gateway;

import com.miqu3iasg.banking_mvp.transaction.service.AbstractE2ETestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class EfiEvpGatewayE2ETest extends AbstractE2ETestSupport {

	private static final String UUID_REGEX = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";
	private static final int UUID_LENGTH = 36;

	private String evpKey;

	@AfterEach
	void cleanup () {
		if (evpKey != null) {
			assertThatCode(() -> evpGateway.deleteEvpKey(evpKey)).doesNotThrowAnyException();

			evpKey = null;
		}
	}

	@Test
	@DisplayName("createEvpKey() should return a well-formed UUID chave")
	void shouldReturnWellFormedUuidChaveWhenCreatingEvpKey () {
		evpKey = evpGateway.createEvpKey();

		assertThat(evpKey)
			.isNotBlank()
			.hasSize(UUID_LENGTH)
			.matches(UUID_REGEX);

		assertThat(listEvpKeysOrFail()).contains(evpKey);
	}

	@Test
	@DisplayName("POST /v2/gn/evp should return 200 with a UUID-formatted chave")
	void shouldReturn200WithWellFormedChaveWhenPostingEvpViaHttp () {
		sandboxClient()
			.post()
			.uri("/v2/gn/evp")
			.exchange()
			.expectStatus().isOk()
			.expectBody()
			.jsonPath("$.chave").isNotEmpty()
			.jsonPath("$.chave").value(raw -> {
				String chave = (String) raw;

				assertThat(chave)
					.isNotBlank()
					.hasSize(UUID_LENGTH)
					.matches(UUID_REGEX);

				evpKey = chave; // register for @AfterEach cleanup
			});
	}

	@Test
	@DisplayName("listEvpKeys() should contain the newly created key with no duplicates or nulls")
	void shouldContainNewlyCreatedKeyWhenListingEvpKeys () {
		evpKey = createEvpKeyOrFail();

		List<String> keys = listEvpKeysOrFail();

		assertThat(keys)
			.contains(evpKey)
			.hasSizeGreaterThanOrEqualTo(1);
	}

	@Test
	@DisplayName("listEvpKeys() should return a structurally valid list regardless of how many keys exist")
	void shouldReturnStructurallyValidListRegardlessOfKeyCount () {
		List<String> keys = listEvpKeysOrFail();

		assertThat(keys.size()).isGreaterThanOrEqualTo(0);
	}

	@Test
	@DisplayName("GET /v2/gn/evp should return 200 with a non-empty chaves array of valid UUIDs")
	void shouldReturn200WithWellFormedChavesArrayWhenListingEvpKeysViaHttp () {
		evpKey = createEvpKeyOrFail();

		sandboxClient()
			.get()
			.uri("/v2/gn/evp")
			.exchange()
			.expectStatus().isOk()
			.expectBody()
			.jsonPath("$.chaves").isArray()
			.jsonPath("$.chaves").isNotEmpty()
			.jsonPath("$.chaves[0]").value(first ->
				assertThat((String) first)
					.isNotBlank()
					.hasSize(UUID_LENGTH)
					.matches(UUID_REGEX)
			);
	}

	@Test
	@DisplayName("deleteEvpKey() should remove the key so it no longer appears in listEvpKeys()")
	void shouldRemoveKeyFromListWhenDeletingExistingEvpKey () {
		evpKey = createEvpKeyOrFail();

		assertThat(listEvpKeysOrFail()).contains(evpKey);

		evpGateway.deleteEvpKey(evpKey);

		assertThat(listEvpKeysOrFail()).doesNotContain(evpKey);

		evpKey = null; // already deleted; skip @AfterEach cleanup
	}

	@Test
	@DisplayName("deleteEvpKey() should be idempotent — deleting twice must not corrupt list state")
	void shouldNotCorruptListStateWhenDeletingSameKeyTwice () {
		evpKey = createEvpKeyOrFail();
		evpGateway.deleteEvpKey(evpKey);

		assertThatCode(() -> evpGateway.deleteEvpKey(evpKey)).doesNotThrowAnyException();

		listEvpKeysOrFail();

		evpKey = null;
	}

	@Test
	@DisplayName("DELETE /v2/gn/evp/{chave} should return 200, 400 or 404 when deleting an already-deleted key")
	void shouldReturnAcceptableStatusWhenDeletingAlreadyDeletedKeyViaHttp () {
		String tmpKey = createEvpKeyOrFail();
		evpGateway.deleteEvpKey(tmpKey);

		assertThat(listEvpKeysOrFail()).doesNotContain(tmpKey);

		sandboxClient()
			.delete()
			.uri("/v2/gn/evp/{chave}", tmpKey)
			.exchange()
			.expectStatus().value(status ->
				assertThat(status)
					.isIn(
						HttpStatus.OK.value(),
						HttpStatus.BAD_REQUEST.value(),
						HttpStatus.NOT_FOUND.value()
					)
			);
	}

	private String createEvpKeyOrFail () {
		String key = evpGateway.createEvpKey();

		assertThat(key)
			.isNotBlank()
			.hasSize(UUID_LENGTH)
			.matches(UUID_REGEX);

		return key;
	}

	private List<String> listEvpKeysOrFail () {
		List<String> keys = evpGateway.listEvpKeys();

		assertThat(keys)
			.isNotNull()
			.doesNotContainNull()
			.doesNotHaveDuplicates()
			.allMatch(k -> k.matches(UUID_REGEX))
			.allMatch(k -> k.length() == UUID_LENGTH);

		return keys;
	}
}
