package com.miqu3iasg.banking.auth.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class AuthMetrics {

    private final MeterRegistry meterRegistry;

    private final Counter loginSuccessCounter;
    private final Counter loginFailureCounter;
    private final Counter tokenRefreshSuccessCounter;
    private final Counter tokenRefreshFailureCounter;
    private final Counter mfaAttemptCounter;
    private final Counter mfaSuccessCounter;
    private final Counter mfaFailureCounter;
    private final Counter registrationStartedCounter;
    private final Counter registrationCompletedCounter;
    private final Counter passwordResetRequestedCounter;
    private final Counter passwordResetCompletedCounter;
    private final Counter lockoutTriggeredCounter;

    private final ConcurrentHashMap<String, Counter> failureReasonCounters = new ConcurrentHashMap<>();

    public AuthMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        this.loginSuccessCounter = Counter.builder("auth.login.success")
            .description("Successful login attempts")
            .register(meterRegistry);

        this.loginFailureCounter = Counter.builder("auth.login.failure")
            .description("Failed login attempts")
            .register(meterRegistry);

        this.tokenRefreshSuccessCounter = Counter.builder("auth.token.refresh.success")
            .description("Successful token refresh attempts")
            .register(meterRegistry);

        this.tokenRefreshFailureCounter = Counter.builder("auth.token.refresh.failure")
            .description("Failed token refresh attempts")
            .register(meterRegistry);

        this.mfaAttemptCounter = Counter.builder("auth.mfa.attempt")
            .description("MFA verification attempts")
            .register(meterRegistry);

        this.mfaSuccessCounter = Counter.builder("auth.mfa.success")
            .description("Successful MFA verifications")
            .register(meterRegistry);

        this.mfaFailureCounter = Counter.builder("auth.mfa.failure")
            .description("Failed MFA verifications")
            .register(meterRegistry);

        this.registrationStartedCounter = Counter.builder("auth.registration.started")
            .description("Registration processes started")
            .register(meterRegistry);

        this.registrationCompletedCounter = Counter.builder("auth.registration.completed")
            .description("Registration processes completed")
            .register(meterRegistry);

        this.passwordResetRequestedCounter = Counter.builder("auth.password_reset.requested")
            .description("Password reset requests")
            .register(meterRegistry);

        this.passwordResetCompletedCounter = Counter.builder("auth.password_reset.completed")
            .description("Password resets completed")
            .register(meterRegistry);

        this.lockoutTriggeredCounter = Counter.builder("auth.lockout.triggered")
            .description("Account lockouts triggered")
            .register(meterRegistry);
    }

    public void recordLoginSuccess() {
        loginSuccessCounter.increment();
    }

    public void recordLoginFailure(String reason) {
        loginFailureCounter.increment();
        getFailureReasonCounter("login", reason).increment();
    }

    public void recordTokenRefreshSuccess() {
        tokenRefreshSuccessCounter.increment();
    }

    public void recordTokenRefreshFailure(String reason) {
        tokenRefreshFailureCounter.increment();
        getFailureReasonCounter("token_refresh", reason).increment();
    }

    public void recordMfaAttempt() {
        mfaAttemptCounter.increment();
    }

    public void recordMfaSuccess() {
        mfaSuccessCounter.increment();
    }

    public void recordMfaFailure() {
        mfaFailureCounter.increment();
    }

    public void recordRegistrationStarted() {
        registrationStartedCounter.increment();
    }

    public void recordRegistrationCompleted() {
        registrationCompletedCounter.increment();
    }

    public void recordPasswordResetRequested() {
        passwordResetRequestedCounter.increment();
    }

    public void recordPasswordResetCompleted() {
        passwordResetCompletedCounter.increment();
    }

    public void recordLockout(String userIdHash) {
        lockoutTriggeredCounter.increment();
        log.warn("Account lockout triggered for user hash: {}", userIdHash);
    }

    private Counter getFailureReasonCounter(String operation, String reason) {
        String key = operation + "_" + reason;
        return failureReasonCounters.computeIfAbsent(key, k ->
            Counter.builder("auth." + operation + ".failure")
                .description("Failed " + operation + " attempts by reason")
                .tag("reason", reason)
                .register(meterRegistry)
        );
    }

    public Timer.Sample startTimer() {
        return Timer.start(meterRegistry);
    }

    public void recordTimer(Timer.Sample sample, String name) {
        sample.stop(Timer.builder(name).register(meterRegistry));
    }
}
