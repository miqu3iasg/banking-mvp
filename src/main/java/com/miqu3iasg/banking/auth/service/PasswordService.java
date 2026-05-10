package com.miqu3iasg.banking.auth.service;

import com.miqu3iasg.banking.auth.config.AuthProperties;
import com.miqu3iasg.banking.auth.exception.PasswordException;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Set;
import java.util.regex.Pattern;

@Slf4j
@Service
public class PasswordService implements PasswordEncoder {

    private static final String HIBP_BASE_URL = "https://api.pwnedpasswords.com/range/";
    private static final Pattern UPPERCASE_PATTERN = Pattern.compile("[A-Z]");
    private static final Pattern LOWERCASE_PATTERN = Pattern.compile("[a-z]");
    private static final Pattern DIGIT_PATTERN = Pattern.compile("[0-9]");
    private static final Pattern SPECIAL_PATTERN = Pattern.compile("[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]");

    private final AuthProperties authProperties;
    private final MeterRegistry meterRegistry;
    private final WebClient hibpWebClient;
    private final SecureRandom secureRandom;
    private final Set<String> commonPasswords;

    public PasswordService(AuthProperties authProperties,
                           MeterRegistry meterRegistry,
                           @Qualifier("hibpWebClient") WebClient hibpWebClient) {
        this.authProperties = authProperties;
        this.meterRegistry = meterRegistry;
        this.hibpWebClient = hibpWebClient;
        this.secureRandom = new SecureRandom();
        this.commonPasswords = loadCommonPasswords();
    }

    private Set<String> loadCommonPasswords() {
        Set<String> passwords = new java.util.HashSet<>();
        String list = """
password123,password123!,123456,12345678,qwerty,abc123,monkey,1234567,letmein,trustno1,dragon,
baseball,football,shadow,master,michael,superman,batman,access,hello,charlie,
donald,password1,password,admin,admin123,root,toor,qwerty123,iloveyou,
welcome,summer,jennifer,aa123456,password1234,1qaz2wsx,654321,666666,7777777,
88888888,999999999,123qwe,123asd,1q2w3e4r,abc123,admin123,letmein1,
mustang,master1,password123,princess,monkey123,baseball1,superman1,
trustno1,dragon1,football1,shadow1,batman1,access1,hello123,
charlie1,david1,jordan23,michelle,justin,ashley,nicole,chelsea,
daniel1,babygirl,hockey,lakers,yankees,jordan,racecar,secret,
tennis,computer,michelle,justin,ashley,nicole,chelsea,buster,
diamond,gfhjkm,harley,hunter,jordan23,michelle,justin,ashley,
nicole,chelsea,buster,diamond,gfhjkm,harley,hunter,jordan23
""".replaceAll("\\s+", "");
        for (String p : list.split(",")) {
            if (!p.isEmpty()) {
                passwords.add(p.toLowerCase());
            }
        }
        return passwords;
    }

    @Override
    public String encode(CharSequence rawPassword) {
        int cost = 12;
        return BCrypt.hashpw(rawPassword.toString(), BCrypt.gensalt(cost, secureRandom));
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        if (rawPassword == null || encodedPassword == null) {
            return false;
        }
        return BCrypt.checkpw(rawPassword.toString(), encodedPassword);
    }

    public void validatePassword(String password) {
        AuthProperties.Password props = authProperties.getPassword();

        if (password == null || password.length() < props.getMinLength()) {
            throw new PasswordException("Password must be at least " + props.getMinLength() + " characters",
                    com.miqu3iasg.banking.auth.exception.AuthFaultCode.PWD_001);
        }

        int complexityScore = 0;
        if (props.isRequireUppercase() && UPPERCASE_PATTERN.matcher(password).find()) {
            complexityScore++;
        }
        if (props.isRequireLowercase() && LOWERCASE_PATTERN.matcher(password).find()) {
            complexityScore++;
        }
        if (props.isRequireDigit() && DIGIT_PATTERN.matcher(password).find()) {
            complexityScore++;
        }
        if (props.isRequireSpecial() && SPECIAL_PATTERN.matcher(password).find()) {
            complexityScore++;
        }

        int requiredScore = (props.isRequireUppercase() ? 1 : 0) +
                (props.isRequireLowercase() ? 1 : 0) +
                (props.isRequireDigit() ? 1 : 0) +
                (props.isRequireSpecial() ? 1 : 0);

        if (complexityScore < requiredScore) {
            throw new PasswordException("Password does not meet complexity requirements",
                    com.miqu3iasg.banking.auth.exception.AuthFaultCode.PWD_001);
        }

        if (isCommonPassword(password)) {
            throw new PasswordException("Password is too common",
                    com.miqu3iasg.banking.auth.exception.AuthFaultCode.PWD_002);
        }

        if (props.isCheckHaveIBeenPwned() && isPasswordBreached(password)) {
            throw new PasswordException("Password found in data breach",
                    com.miqu3iasg.banking.auth.exception.AuthFaultCode.PWD_002);
        }
    }

    public boolean isCommonPassword(String password) {
        return commonPasswords.contains(password.toLowerCase());
    }

    public boolean isPasswordBreached(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            String sha1 = HexFormat.of().formatHex(digest.digest(password.getBytes(StandardCharsets.UTF_8)));
            String prefix = sha1.substring(0, 5);
            String suffix = sha1.substring(5);

            log.debug("Password hash prefix for HaveIBeenPwned check: {}", prefix);

            String response = queryHaveIBeenPwned(prefix);
            if (response == null || response.isEmpty()) {
                return false;
            }

            String[] lines = response.split("\r\n|\r|\n");
            for (String line : lines) {
                String[] parts = line.split(":");
                if (parts.length >= 1 && parts[0].equalsIgnoreCase(suffix)) {
                    return true;
                }
            }
            return false;
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-1 not available", e);
            return false;
        }
    }

    private String queryHaveIBeenPwned(String prefix) {
        try {
            String response = hibpWebClient.get()
                    .uri("/range/" + prefix)
                    .header("Add-Padding", "true")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(5));

            if (response == null) {
                meterRegistry.counter("hibp.check.failure").increment();
                log.warn("HIBP check returned empty response");
                if (authProperties.getPassword().isHibpFailClosed()) {
                    throw new PasswordException("Password breach check unavailable — service rejecting passwords",
                            com.miqu3iasg.banking.auth.exception.AuthFaultCode.PWD_002);
                }
                return "";
            }
            return response;
        } catch (Exception e) {
            meterRegistry.counter("hibp.check.failure").increment();
            if (authProperties.getPassword().isHibpFailClosed()) {
                log.error("HIBP check failed, failing closed");
                throw new PasswordException("Password breach check unavailable — service rejecting passwords",
                        com.miqu3iasg.banking.auth.exception.AuthFaultCode.PWD_002);
            }
            log.warn("HIBP check failed, failing open");
            return "";
        }
    }

    public boolean isPasswordInHistory(String rawPassword, Set<String> passwordHistory) {
        return passwordHistory.stream()
                .anyMatch(storedHash -> BCrypt.checkpw(rawPassword, storedHash));
    }

    public String generateRandomPassword(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(secureRandom.nextInt(chars.length())));
        }
        return sb.toString();
    }

    public String hashForStorage(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
