package com.meridianmart.recommendation;

import com.meridianmart.dto.ProductDto;
import com.meridianmart.model.Product;
import com.meridianmart.model.Recommendation;
import com.meridianmart.model.User;
import com.meridianmart.repository.*;
import com.meridianmart.service.AppConfigService;
import com.meridianmart.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final RecommendationRepository recommendationRepository;
    private final BehaviorEventRepository behaviorEventRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final FeatureFlagRepository featureFlagRepository;
    private final ProductService productService;
    private final AppConfigService appConfigService;

    @Value("${app.recommendations.ttl-minutes:60}")
    private int defaultTtlMinutes;

    @Value("${app.recommendations.min-shared-interactions:3}")
    private int defaultMinSharedInteractions;

    @Value("${app.recommendations.top-n:10}")
    private int defaultTopN;

    @Value("${app.recommendations.cold-start-days:30}")
    private int defaultColdStartDays;

    @Value("${app.recommendations.similarity-algorithm:COSINE}")
    private String defaultSimilarityAlgorithm;

    private int ttlMinutes() { return appConfigService.getIntConfig("recommendations.ttl-minutes", defaultTtlMinutes); }
    private int minSharedInteractions() { return appConfigService.getIntConfig("recommendations.min-shared-interactions", defaultMinSharedInteractions); }
    private int topN() { return appConfigService.getIntConfig("recommendations.top-n", defaultTopN); }
    private int coldStartDays() { return appConfigService.getIntConfig("recommendations.cold-start-days", defaultColdStartDays); }
    private String similarityAlgorithm() { return appConfigService.getStringConfig("recommendations.similarity-algorithm", defaultSimilarityAlgorithm); }

    @Transactional(readOnly = true)
    public List<ProductDto> getRecommendations(User user) {
        if (!isRecommendationsEnabled(user.getStoreId())) {
            return Collections.emptyList();
        }

        LocalDateTime ttlThreshold = LocalDateTime.now().minusMinutes(ttlMinutes());
        List<Recommendation> cached = recommendationRepository.findFreshByUserId(user.getId(), ttlThreshold);

        if (!cached.isEmpty()) {
            log.info("Returning {} cached recommendations for user {}", cached.size(), user.getId());
            return cached.stream()
                    .limit(topN())
                    .map(r -> productService.toDto(r.getProduct()))
                    .collect(Collectors.toList());
        }

        return generateAndCacheRecommendations(user);
    }

    @Transactional
    public List<ProductDto> generateAndCacheRecommendations(User user) {
        long interactionCount = behaviorEventRepository.countByUserId(user.getId());
        String storeId = user.getStoreId();

        List<Product> recommended;
        if (interactionCount < minSharedInteractions()) {
            log.info("Cold start for user {} (interactions={})", user.getId(), interactionCount);
            recommended = getColdStartProducts(user.getId(), storeId);
        } else {
            recommended = computeCollaborativeFiltering(user.getId(), storeId);
            if (recommended.isEmpty()) {
                recommended = getColdStartProducts(user.getId(), storeId);
            }
        }

        int topN = topN();
        recommended = recommended.stream().limit(topN).collect(Collectors.toList());

        recommendationRepository.deleteByUserId(user.getId());

        List<Recommendation> recs = new ArrayList<>();
        for (int i = 0; i < recommended.size(); i++) {
            Product product = recommended.get(i);
            double score = topN - i;
            Recommendation rec = Recommendation.builder()
                    .user(user)
                    .product(product)
                    .score(score)
                    .cachedAt(LocalDateTime.now())
                    .build();
            recs.add(rec);
        }
        recommendationRepository.saveAll(recs);
        log.info("Cached {} recommendations for user {}", recs.size(), user.getId());

        return recommended.stream()
                .map(productService::toDto)
                .collect(Collectors.toList());
    }

    private List<Product> computeCollaborativeFiltering(Long targetUserId, String storeId) {
        List<Object[]> allInteractions = behaviorEventRepository.findAllUserProductInteractionCountsByStoreId(storeId);

        Map<Long, Map<Long, Double>> userItemMatrix = new HashMap<>();
        for (Object[] row : allInteractions) {
            Long userId = ((Number) row[0]).longValue();
            Long productId = ((Number) row[1]).longValue();
            double count = ((Number) row[2]).doubleValue();
            userItemMatrix.computeIfAbsent(userId, k -> new HashMap<>()).put(productId, count);
        }

        Map<Long, Double> targetVector = userItemMatrix.get(targetUserId);
        if (targetVector == null || targetVector.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> targetProductIds = behaviorEventRepository.findProductIdsByUserId(targetUserId);
        Set<Long> interactedProducts = new HashSet<>(targetProductIds);

        Map<Long, Double> productScores = new HashMap<>();

        for (Map.Entry<Long, Map<Long, Double>> entry : userItemMatrix.entrySet()) {
            Long otherUserId = entry.getKey();
            if (otherUserId.equals(targetUserId)) continue;

            Map<Long, Double> otherVector = entry.getValue();
            Set<Long> sharedProducts = new HashSet<>(targetVector.keySet());
            sharedProducts.retainAll(otherVector.keySet());

            if (sharedProducts.size() < minSharedInteractions()) {
                continue;
            }

            double similarity = "PEARSON".equalsIgnoreCase(similarityAlgorithm())
                    ? computePearsonSimilarity(targetVector, otherVector, sharedProducts)
                    : computeCosineSimilarity(targetVector, otherVector, sharedProducts);
            if (similarity <= 0) continue;

            for (Map.Entry<Long, Double> productEntry : otherVector.entrySet()) {
                Long productId = productEntry.getKey();
                if (!interactedProducts.contains(productId)) {
                    productScores.merge(productId, similarity * productEntry.getValue(), Double::sum);
                }
            }
        }

        if (productScores.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> topProductIds = productScores.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(topN())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        return productRepository.findByIdInAndStoreId(topProductIds, storeId);
    }

    private double computeCosineSimilarity(Map<Long, Double> v1, Map<Long, Double> v2, Set<Long> shared) {
        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;

        for (Long productId : shared) {
            double val1 = v1.getOrDefault(productId, 0.0);
            double val2 = v2.getOrDefault(productId, 0.0);
            dotProduct += val1 * val2;
        }

        for (double val : v1.values()) norm1 += val * val;
        for (double val : v2.values()) norm2 += val * val;

        if (norm1 == 0 || norm2 == 0) return 0.0;
        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }

    private double computePearsonSimilarity(Map<Long, Double> v1, Map<Long, Double> v2, Set<Long> shared) {
        if (shared.size() < 2) return 0.0;

        double sum1 = 0.0, sum2 = 0.0;
        for (Long id : shared) {
            sum1 += v1.getOrDefault(id, 0.0);
            sum2 += v2.getOrDefault(id, 0.0);
        }
        double mean1 = sum1 / shared.size();
        double mean2 = sum2 / shared.size();

        double numerator = 0.0, denom1 = 0.0, denom2 = 0.0;
        for (Long id : shared) {
            double d1 = v1.getOrDefault(id, 0.0) - mean1;
            double d2 = v2.getOrDefault(id, 0.0) - mean2;
            numerator += d1 * d2;
            denom1 += d1 * d1;
            denom2 += d2 * d2;
        }

        if (denom1 == 0 || denom2 == 0) return 0.0;
        return numerator / (Math.sqrt(denom1) * Math.sqrt(denom2));
    }

    private List<Product> getColdStartProducts(Long userId, String storeId) {
        List<Long> interacted = behaviorEventRepository.findProductIdsByUserId(userId);
        Set<Long> interactedSet = new HashSet<>(interacted);

        LocalDateTime since = LocalDateTime.now().minusDays(coldStartDays());

        // Prefer category-level popularity for the user's interacted categories
        List<String> preferredCategories = behaviorEventRepository.findInteractedCategoriesByUserId(userId);
        List<Object[]> popularRows;
        if (!preferredCategories.isEmpty()) {
            popularRows = behaviorEventRepository.findProductPopularityByCategoriesSince(since, storeId, preferredCategories);
        } else {
            popularRows = behaviorEventRepository.findProductPopularitySinceByStoreId(since, storeId);
        }

        List<Long> popularIds = popularRows.stream()
                .map(row -> ((Number) row[0]).longValue())
                .filter(id -> !interactedSet.contains(id))
                .limit(7)
                .collect(Collectors.toList());

        List<Product> result = popularIds.isEmpty()
                ? new ArrayList<>()
                : productRepository.findByIdInAndStoreId(popularIds, storeId);

        List<Product> newArrivals = productRepository.findNewArrivalsByStoreId(storeId, PageRequest.of(0, 5));
        for (Product p : newArrivals) {
            if (!interactedSet.contains(p.getId()) && result.stream().noneMatch(r -> r.getId().equals(p.getId()))) {
                result.add(p);
            }
        }

        if (result.isEmpty()) {
            result = productRepository.findByStoreId(storeId, PageRequest.of(0, topN())).getContent();
        }

        return result;
    }

    private List<ProductDto> getColdStartRecommendations(Long userId, String storeId) {
        return getColdStartProducts(userId, storeId).stream()
                .limit(topN())
                .map(productService::toDto)
                .collect(Collectors.toList());
    }

    private boolean isRecommendationsEnabled(String storeId) {
        return featureFlagRepository.findByFlagNameAndStoreId("RECOMMENDATIONS_ENABLED", storeId)
                .or(() -> featureFlagRepository.findByFlagName("RECOMMENDATIONS_ENABLED"))
                .map(f -> f.isEnabled())
                .orElse(true);
    }

    @Transactional
    public void refreshAllRecommendations() {
        log.info("Starting nightly recommendation refresh at {}", LocalDateTime.now());
        List<User> users = userRepository.findAll();
        int count = 0;
        for (User user : users) {
            if (user.getRole() == User.Role.SHOPPER && !user.isAnonymized()) {
                try {
                    generateAndCacheRecommendations(user);
                    count++;
                } catch (Exception e) {
                    log.error("Failed to refresh recommendations for user {}: {}", user.getId(), e.getMessage());
                }
            }
        }
        log.info("Nightly recommendation refresh complete. Refreshed {} users at {}", count, LocalDateTime.now());
    }
}
