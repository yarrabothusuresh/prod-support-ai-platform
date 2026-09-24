package com.example.prodsupport.metrics;

import com.example.prodsupport.metrics.model.MetricSampleDto;
import com.example.prodsupport.metrics.service.TrendAnalysisService;
import com.example.prodsupport.metrics.service.TrendAnalysisService.TrendDirection;
import com.example.prodsupport.metrics.service.TrendAnalysisService.TrendResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TrendAnalysisServiceTest {

    private TrendAnalysisService trendAnalysisService;

    @BeforeEach
    void setUp() {
        trendAnalysisService = new TrendAnalysisService();
    }

    @Test
    @DisplayName("Should classify clear upward trajectory as INCREASING")
    void shouldClassifyIncreasingTrend() {
        List<MetricSampleDto> samples = new ArrayList<>();
        Instant now = Instant.now();
        // Values rising from 10 to 100
        for (int i = 0; i < 9; i++) {
            samples.add(new MetricSampleDto(now.plusSeconds(i * 60), 10.0 + (i * 10.0)));
        }

        TrendResult result = trendAnalysisService.analyzeTrend(samples);

        assertThat(result.direction()).isEqualTo(TrendDirection.INCREASING);
        assertThat(result.min()).isEqualTo(10.0);
        assertThat(result.max()).isEqualTo(90.0);
        assertThat(result.firstValue()).isEqualTo(10.0);
        assertThat(result.latestValue()).isEqualTo(90.0);
        assertThat(result.sampleCount()).isEqualTo(9);
        assertThat(result.description()).containsIgnoringCase("increasing");
    }

    @Test
    @DisplayName("Should classify clear downward trajectory as DECREASING")
    void shouldClassifyDecreasingTrend() {
        List<MetricSampleDto> samples = new ArrayList<>();
        Instant now = Instant.now();
        // Values falling from 100 to 20
        for (int i = 0; i < 9; i++) {
            samples.add(new MetricSampleDto(now.plusSeconds(i * 60), 100.0 - (i * 10.0)));
        }

        TrendResult result = trendAnalysisService.analyzeTrend(samples);

        assertThat(result.direction()).isEqualTo(TrendDirection.DECREASING);
        assertThat(result.firstValue()).isEqualTo(100.0);
        assertThat(result.latestValue()).isEqualTo(20.0);
        assertThat(result.description()).containsIgnoringCase("decreasing");
    }

    @Test
    @DisplayName("Should classify steady metrics within 10% change as STABLE")
    void shouldClassifyStableTrend() {
        List<MetricSampleDto> samples = new ArrayList<>();
        Instant now = Instant.now();
        // Slight fluctuation around 50
        double[] vals = {50.0, 51.0, 49.5, 50.2, 50.8, 49.9, 50.1, 50.3, 50.0};
        for (int i = 0; i < vals.length; i++) {
            samples.add(new MetricSampleDto(now.plusSeconds(i * 60), vals[i]));
        }

        TrendResult result = trendAnalysisService.analyzeTrend(samples);

        assertThat(result.direction()).isEqualTo(TrendDirection.STABLE);
        assertThat(result.description()).containsIgnoringCase("stable");
    }

    @Test
    @DisplayName("Should return INSUFFICIENT_DATA when sample size is less than 3")
    void shouldReturnInsufficientDataForFewSamples() {
        TrendResult emptyResult = trendAnalysisService.analyzeTrend(List.of());
        assertThat(emptyResult.direction()).isEqualTo(TrendDirection.INSUFFICIENT_DATA);

        TrendResult nullResult = trendAnalysisService.analyzeTrend(null);
        assertThat(nullResult.direction()).isEqualTo(TrendDirection.INSUFFICIENT_DATA);

        TrendResult twoPoints = trendAnalysisService.analyzeTrend(List.of(
                new MetricSampleDto(Instant.now(), 5.0),
                new MetricSampleDto(Instant.now().plusSeconds(60), 10.0)
        ));
        assertThat(twoPoints.direction()).isEqualTo(TrendDirection.INSUFFICIENT_DATA);
        assertThat(twoPoints.sampleCount()).isEqualTo(2);
    }
}
