package com.miqu3iasg.banking.auth.service;

import com.miqu3iasg.banking.auth.config.AuthProperties;
import com.miqu3iasg.banking.auth.domain.AccountStatus;
import com.miqu3iasg.banking.auth.domain.LoginAttempt;
import com.miqu3iasg.banking.auth.domain.User;
import com.miqu3iasg.banking.auth.repository.LoginAttemptRepository;
import com.miqu3iasg.banking.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class LockoutService {

    private static final String LOCKOUT_ATTEMPTS_KEY = "lockout:attempts:%s";
    private static final long ATTEMPTS_WINDOW_MINUTES = 15;

    private final UserRepository userRepository;
    private final LoginAttemptRepository loginAttemptRepository;
    private final StringRedisTemplate redisTemplate;
    private final AuthProperties authProperties;

    @Transactional
    public void recordFailedLogin(User user, String ipHash, String userAgentHash, String failureReason) {
        incrementRedisCounter(ipHash);

        boolean willLockOut = false;
        if (user != null) {
            user.incrementFailedAttempts();
            willLockOut = user.getFailedLoginAttempts() >= authProperties.getLockout().getMaxFailedAttempts();
            if (willLockOut) {
                lockUser(user);
            }
            userRepository.save(user);
        }

        LoginAttempt attempt = LoginAttempt.builder()
            .userId(user != null ? user.getId() : null)
            .emailHash(user != null ? user.getEmailHash() : null)
            .ipHash(ipHash)
            .userAgentHash(userAgentHash)
            .success(false)
            .failureReason(failureReason)
            .lockedOut(willLockOut)
            .build();

        loginAttemptRepository.save(attempt);

        log.debug("Recorded failed login attempt for user: {}, attempts: {}, lockedOut: {}",
            user != null ? user.getId() : "unknown",
            user != null ? user.getFailedLoginAttempts() : 0,
            willLockOut);
    }

    @Transactional
    public void recordSuccessfulLogin(User user, String ipHash, String userAgentHash) {
        LoginAttempt attempt = LoginAttempt.builder()
            .userId(user.getId())
            .emailHash(user.getEmailHash())
            .ipHash(ipHash)
            .userAgentHash(userAgentHash)
            .success(true)
            .build();

        loginAttemptRepository.save(attempt);

        user.resetFailedAttempts();
        userRepository.save(user);

        resetRedisCounter(ipHash);
    }

    @Transactional
    public void lockUser(User user) {
        long lockoutDuration = calculateLockoutDuration(user.getFailedLoginAttempts());
        Instant lockedUntil = Instant.now().plus(lockoutDuration, ChronoUnit.SECONDS);

        user.lock(lockedUntil);
        user.setStatus(AccountStatus.LOCKED);

        log.info("User locked: {} until {}", user.getId(), lockedUntil);
    }

    @Transactional
    public void unlockUser(User user) {
        user.unlock();
        if (user.getStatus() == AccountStatus.LOCKED) {
            user.setStatus(AccountStatus.ACTIVE);
        }
        userRepository.save(user);

        log.info("User unlocked: {}", user.getId());
    }

    private long calculateLockoutDuration(int failedAttempts) {
        AuthProperties.Lockout lockout = authProperties.getLockout();
        int lockoutNumber = Math.max(0, failedAttempts - lockout.getMaxFailedAttempts());
        double multiplied = lockout.getInitialLockoutSeconds() * Math.pow(lockout.getLockoutMultiplier(), lockoutNumber);
        return Math.min((long) multiplied, lockout.getMaxLockoutSeconds());
    }

    public boolean isLocked(User user) {
        return user != null && user.isLocked();
    }

    public boolean isRateLimited(String ipHash) {
        try {
            String attemptsStr = redisTemplate.opsForValue().get(String.format(LOCKOUT_ATTEMPTS_KEY, ipHash));
            if (attemptsStr == null) {
                return false;
            }
            int attempts = Integer.parseInt(attemptsStr);
            return attempts >= authProperties.getLockout().getMaxFailedAttempts();
        } catch (Exception e) {
            log.warn("Redis unavailable for rate limit check, falling through: {}", e.getMessage());
            return false;
        }
    }

    public long getRemainingLockoutSeconds(User user) {
        if (user == null || user.getLockedUntil() == null) {
            return 0;
        }
        Instant now = Instant.now();
        if (now.isAfter(user.getLockedUntil())) {
            return 0;
        }
        return now.until(user.getLockedUntil(), java.time.temporal.ChronoUnit.SECONDS);
    }

    private void incrementRedisCounter(String ipHash) {
        try {
            String key = String.format(LOCKOUT_ATTEMPTS_KEY, ipHash);
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1) {
                redisTemplate.expire(key, java.time.Duration.ofMinutes(ATTEMPTS_WINDOW_MINUTES));
            }
        } catch (Exception e) {
            log.warn("Failed to increment Redis lockout counter for {}: {}", ipHash, e.getMessage());
        }
    }

    private void resetRedisCounter(String ipHash) {
        try {
            redisTemplate.delete(String.format(LOCKOUT_ATTEMPTS_KEY, ipHash));
        } catch (Exception e) {
            log.warn("Failed to reset Redis lockout counter for {}: {}", ipHash, e.getMessage());
        }
    }
}
