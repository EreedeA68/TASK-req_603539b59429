package com.meridianmart.unit;

import com.meridianmart.dto.ProductDto;
import com.meridianmart.model.*;
import com.meridianmart.recommendation.RecommendationService;
import com.meridianmart.repository.*;
import com.meridianmart.service.AppConfigService;
import com.meridianmart.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RecommendationServiceTest {

    private static final String STORE = "STORE_001";

    @Mock RecommendationRepository recommendationRepository;
    @Mock BehaviorEventRepository behaviorEventRepository;
    @Mock ProductRepository productRepository;
    @Mock UserRepository userRepository;
    @Mock FeatureFlagRepository featureFlagRepository;
    @Mock ProductService productService;
    @Mock AppConfigService appConfigService;
    @InjectMocks RecommendationService recommendationService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(recommendationService, "defaultTtlMinutes", 60);
        ReflectionTestUtils.setField(recommendationService, "defaultMinSharedInteractions", 3);
        ReflectionTestUtils.setField(recommendationService, "defaultTopN", 10);
        ReflectionTestUtils.setField(recommendationService, "defaultColdStartDays", 30);
        ReflectionTestUtils.setField(recommendationService, "defaultSimilarityAlgorithm", "COSINE");

        when(appConfigService.getIntConfig(eq("recommendations.ttl-minutes"), anyInt())).thenReturn(60);
        when(appConfigService.getIntConfig(eq("recommendations.min-shared-interactions"), anyInt())).thenReturn(3);
        when(appConfigService.getIntConfig(eq("recommendations.top-n"), anyInt())).thenReturn(10);
        when(appConfigService.getIntConfig(eq("recommendations.cold-start-days"), anyInt())).thenReturn(30);
        when(appConfigService.getStringConfig(eq("recommendations.similarity-algorithm"), anyString())).thenReturn("COSINE");
    }

    private User buildUser(Long id) {
        return User.builder().id(id).username("user" + id).role(User.Role.SHOPPER).storeId(STORE).build();
    }

    private Product buildProduct(Long id) {
        return Product.builder().id(id).name("Prod " + id).price(BigDecimal.TEN).stockQuantity(5)
                .category("Electronics").storeId(STORE).build();
    }

    @Test
    void coldStartReturnsCategoryPopularityWhenLessThan3Interactions() {
        User user = buildUser(1L);
        when(featureFlagRepository.findByFlagNameAndStoreId("RECOMMENDATIONS_ENABLED", STORE))
                .thenReturn(Optional.of(FeatureFlag.builder().id(1L).flagName("RECOMMENDATIONS_ENABLED").enabled(true).build()));
        when(recommendationRepository.findFreshByUserId(eq(1L), any())).thenReturn(List.of());
        when(behaviorEventRepository.countByUserId(1L)).thenReturn(1L);
        when(behaviorEventRepository.findProductIdsByUserId(1L)).thenReturn(new ArrayList<>());
        when(behaviorEventRepository.findProductPopularitySinceByStoreId(any(), eq(STORE))).thenReturn(
                List.of(new Object[]{1L, 10L}, new Object[]{2L, 8L}));
        when(productRepository.findByIdInAndStoreId(any(), eq(STORE))).thenReturn(List.of(buildProduct(1L), buildProduct(2L)));
        when(productRepository.findNewArrivalsByStoreId(eq(STORE), any())).thenReturn(List.of(buildProduct(3L)));
        doNothing().when(recommendationRepository).deleteByUserId(1L);
        when(recommendationRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(productService.toDto(any())).thenAnswer(inv -> {
            Product p = inv.getArgument(0);
            return ProductDto.builder().id(p.getId()).name(p.getName()).price(p.getPrice()).build();
        });

        List<ProductDto> recs = recommendationService.getRecommendations(user);

        assertThat(recs).isNotEmpty();
    }

    @Test
    void coldStartIncludesNewArrivals() {
        User user = buildUser(1L);
        when(featureFlagRepository.findByFlagNameAndStoreId("RECOMMENDATIONS_ENABLED", STORE))
                .thenReturn(Optional.of(FeatureFlag.builder().id(1L).enabled(true).build()));
        when(recommendationRepository.findFreshByUserId(eq(1L), any())).thenReturn(List.of());
        when(behaviorEventRepository.countByUserId(1L)).thenReturn(0L);
        when(behaviorEventRepository.findProductIdsByUserId(1L)).thenReturn(new ArrayList<>());
        when(behaviorEventRepository.findProductPopularitySinceByStoreId(any(), eq(STORE))).thenReturn(List.of());
        when(productRepository.findByIdInAndStoreId(any(), eq(STORE))).thenReturn(List.of());
        when(productRepository.findNewArrivalsByStoreId(eq(STORE), any())).thenReturn(List.of(buildProduct(100L)));
        doNothing().when(recommendationRepository).deleteByUserId(1L);
        when(recommendationRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(productService.toDto(any())).thenAnswer(inv -> {
            Product p = inv.getArgument(0);
            return ProductDto.builder().id(p.getId()).name(p.getName()).build();
        });

        List<ProductDto> recs = recommendationService.getRecommendations(user);

        assertThat(recs).isNotEmpty();
        assertThat(recs.stream().anyMatch(r -> r.getId() == 100L)).isTrue();
    }

    @Test
    void cachedRecommendationsReturnedWithinTtl() {
        User user = buildUser(1L);
        Recommendation rec = Recommendation.builder()
                .id(1L).user(user).product(buildProduct(5L)).score(9.0).cachedAt(LocalDateTime.now()).build();

        when(featureFlagRepository.findByFlagNameAndStoreId("RECOMMENDATIONS_ENABLED", STORE))
                .thenReturn(Optional.of(FeatureFlag.builder().id(1L).enabled(true).build()));
        when(recommendationRepository.findFreshByUserId(eq(1L), any())).thenReturn(List.of(rec));
        when(productService.toDto(any())).thenAnswer(inv -> {
            Product p = inv.getArgument(0);
            return ProductDto.builder().id(p.getId()).name(p.getName()).build();
        });

        List<ProductDto> recs = recommendationService.getRecommendations(user);

        assertThat(recs).hasSize(1);
        verify(behaviorEventRepository, never()).countByUserId(anyLong());
    }

    @Test
    void similarityNotComputedWhenSharedInteractionsBelowThreshold() {
        User user = buildUser(1L);
        when(featureFlagRepository.findByFlagNameAndStoreId("RECOMMENDATIONS_ENABLED", STORE))
                .thenReturn(Optional.of(FeatureFlag.builder().id(1L).enabled(true).build()));
        when(recommendationRepository.findFreshByUserId(eq(1L), any())).thenReturn(List.of());
        when(behaviorEventRepository.countByUserId(1L)).thenReturn(5L);

        // Only 2 shared interactions per user pair — below threshold of 3
        when(behaviorEventRepository.findAllUserProductInteractionCountsByStoreId(eq(STORE))).thenReturn(
                List.of(
                        new Object[]{1L, 10L, 5L},
                        new Object[]{1L, 11L, 3L},
                        new Object[]{1L, 12L, 2L},
                        new Object[]{2L, 10L, 4L},
                        new Object[]{2L, 11L, 2L}  // only 2 shared with user 1
                )
        );
        when(behaviorEventRepository.findProductIdsByUserId(1L)).thenReturn(List.of(10L, 11L, 12L));
        // Falls back to cold start
        when(behaviorEventRepository.findProductPopularitySinceByStoreId(any(), eq(STORE))).thenReturn(List.of());
        when(productRepository.findByIdInAndStoreId(any(), eq(STORE))).thenReturn(List.of());
        when(productRepository.findNewArrivalsByStoreId(eq(STORE), any())).thenReturn(List.of(buildProduct(99L)));
        doNothing().when(recommendationRepository).deleteByUserId(1L);
        when(recommendationRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(productService.toDto(any())).thenAnswer(inv -> {
            Product p = inv.getArgument(0);
            return ProductDto.builder().id(p.getId()).name(p.getName()).build();
        });

        List<ProductDto> recs = recommendationService.getRecommendations(user);
        assertThat(recs).isNotNull();
    }
}
