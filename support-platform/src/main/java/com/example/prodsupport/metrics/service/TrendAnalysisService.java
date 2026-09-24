package com.example.prodsupport.metrics.service;

import com.example.prodsupport.metrics.model.MetricSampleDto;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Deterministic metric trend analyzer.
 * Evaluates metric series over time to classify behavior without relying on LLM numerical hallucinations.
 */
@Service
public class TrendAnalysisService {

    public enum TrendDirection {
        INCREASING,
        DECREASING,
        STABLE,
        INSUFFICIENT_DATA
    }

    public record TrendResult(
            TrendDirection direction,
            double min,
            double max,
            double average,
            double firstValue,
            double latestValue,
            int sampleCount,
            String description
    ) {}

    /**
     * Analyzes samples using a robust third-partition window comparison.
     * Computes the average of the earliest 1/3 of samples and compares it
     * against the average of the latest 1/3 of samples.
     *
     * @param samples List of time-ordered metric samples
     * @return TrendResult with deterministic classification
     */
    public TrendResult analyzeTrend(List<MetricSampleDto> samples) {
        if (samples == null || samples.size() < 3) {
            double singleVal = (samples != null && !samples.isEmpty()) ? samples.get(0).value() : 0.0;
            return new TrendResult(
                    TrendDirection.INSUFFICIENT_DATA,
                    singleVal,
                    singleVal,
                    singleVal,
                    singleVal,
                    singleVal,
                    samples != null ? samples.size() : 0,
                    "Insufficient data samples to compute a reliable trend."
            );
        }

        int n = samples.size();
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        double sum = 0.0;

        for (MetricSampleDto s : samples) {
            double v = s.value();
            if (v < min) min = v;
            if (v > max) max = v;
            sum += v;
        }

        double average = sum / n;
        double first = samples.get(0).value();
        double latest = samples.get(n - 1).value();

        // Third-partition comparison
        int partitionSize = Math.max(1, n / 3);
        double firstThirdSum = 0.0;
        for (int i = 0; i < partitionSize; i++) {
            firstThirdSum += samples.get(i).value();
        }
        double firstThirdAvg = firstThirdSum / partitionSize;

        double lastThirdSum = 0.0;
        for (int i = n - partitionSize; i < n; i++) {
            lastThirdSum += samples.get(i).value();
        }
        double lastThirdAvg = lastThirdSum / partitionSize;

        double delta = lastThirdAvg - firstThirdAvg;
        double relativeBase = Math.abs(firstThirdAvg) > 0.001 ? Math.abs(firstThirdAvg) : 1.0;
        double relativeChange = delta / relativeBase;

        // Threshold of 5% change to indicate trend
        TrendDirection direction;
        String desc;

        if (relativeChange > 0.05 && delta > 0.0001) {
            direction = TrendDirection.INCREASING;
            desc = String.format("Trend is INCREASING (+%.1f%% from initial baseline)", relativeChange * 100);
        } else if (relativeChange < -0.05 && delta < -0.0001) {
            direction = TrendDirection.DECREASING;
            desc = String.format("Trend is DECREASING (%.1f%% from initial baseline)", relativeChange * 100);
        } else {
            direction = TrendDirection.STABLE;
            desc = "Trend is STABLE within normal operational variance.";
        }

        return new TrendResult(
                direction,
                min,
                max,
                average,
                first,
                latest,
                n,
                desc
        );
    }
}
