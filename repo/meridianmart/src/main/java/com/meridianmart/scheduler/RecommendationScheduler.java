package com.meridianmart.scheduler;

import com.meridianmart.recommendation.RecommendationService;
import com.meridianmart.repository.NonceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class RecommendationScheduler {

    private final RecommendationService recommendationService;
    private final NonceRepository nonceRepository;

    @Scheduled(cron = "0 0 2 * * *")
    public void refreshRecommendations() {
        LocalDateTime start = LocalDateTime.now();
        log.info("Recommendation refresh job started at {}", start);
        try {
            recommendationService.refreshAllRecommendations();
        } catch (Exception e) {
            log.error("Recommendation refresh job failed: {}", e.getMessage(), e);
        }
        log.info("Recommendation refresh job completed in {}ms",
                java.time.Duration.between(start, LocalDateTime.now()).toMillis());
    }

    @Scheduled(fixedDelay = 300_000)
    public void cleanupExpiredNonces() {
        LocalDateTime expiryCutoff = LocalDateTime.now().minusSeconds(300);
        nonceRepository.deleteExpired(expiryCutoff);
        log.debug("Expired nonces cleaned up (older than {})", expiryCutoff);
    }
}
