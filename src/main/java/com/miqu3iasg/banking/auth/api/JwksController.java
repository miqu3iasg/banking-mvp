package com.miqu3iasg.banking.auth.api;

import com.miqu3iasg.banking.auth.security.JwtKeyProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.Map;

@RestController
@RequestMapping("/.well-known")
@RequiredArgsConstructor
@Tag(name = "JWKS", description = "JSON Web Key Set endpoint")
public class JwksController {

    private final JwtKeyProvider jwtKeyProvider;

    @GetMapping(value = "/jwks.json", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get JSON Web Key Set for token validation")
    public Map<String, Object> getJwks() {
        RSAPublicKey publicKey = (RSAPublicKey) jwtKeyProvider.getPublicKey();
        byte[] modulus = stripLeadingZeroByte(publicKey.getModulus().toByteArray());
        byte[] exponent = stripLeadingZeroByte(publicKey.getPublicExponent().toByteArray());

        String n = Base64.getUrlEncoder().withoutPadding().encodeToString(modulus);
        String e = Base64.getUrlEncoder().withoutPadding().encodeToString(exponent);

        return Map.of(
            "keys", java.util.List.of(
                Map.of(
                    "kty", "RSA",
                    "use", "sig",
                    "alg", "RS256",
                    "kid", jwtKeyProvider.getKeyId(),
                    "n", n,
                    "e", e
                )
            )
        );
    }

    private byte[] stripLeadingZeroByte(byte[] bytes) {
        if (bytes.length > 1 && bytes[0] == 0) {
            return java.util.Arrays.copyOfRange(bytes, 1, bytes.length);
        }
        return bytes;
    }
}
