package com.tradingbot.trading;

import com.tradingbot.config.ScoringProperties;
import com.tradingbot.entity.SentimentAnalysis;
import com.tradingbot.entity.enums.SentimentType;
import com.tradingbot.indicators.IndicatorResult;
import com.tradingbot.repository.SentimentAnalysisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScoringEngine {

    private final ScoringProperties scoringProperties;
    private final SentimentAnalysisRepository sentimentRepository;

    public TradeScore score(String symbol, IndicatorResult indicators) {
        ScoringProperties.Weights w = scoringProperties.getWeights();

        // --- Sentiment score (0-100) ---
        double sentimentScore = calculateSentimentScore(symbol);

        // --- Trend score (0-100) ---
        double trendScore = calculateTrendScore(indicators);

        // --- Volume score (0-100) ---
        double volumeScore = calculateVolumeScore(indicators);

        // --- RSI score (0-100, where mid-range is neutral) ---
        double rsiScore = calculateRsiScore(indicators.getRsi14());

        // Weighted total
        double total = (sentimentScore * w.getSentiment())
                + (trendScore * w.getTrend())
                + (volumeScore * w.getVolume())
                + (rsiScore * w.getRsi());

        total = Math.max(0, Math.min(100, total));

        // Confidence: average of sentiment confidence and indicator signal strength
        int confidence = calculateConfidence(symbol, indicators, total);

        String action;
        if (total >= scoringProperties.getBuyThreshold()) action = "BUY";
        else if (total <= scoringProperties.getSellThreshold()) action = "SELL";
        else action = "HOLD";

        TradeScore score = TradeScore.builder()
                .symbol(symbol)
                .totalScore(total)
                .sentimentScore(sentimentScore)
                .trendScore(trendScore)
                .volumeScore(volumeScore)
                .rsiScore(rsiScore)
                .confidence(confidence)
                .action(action)
                .build();

        log.info("Score for {} — total={}, sentiment={}, trend={}, vol={}, rsi={} => {}",
                symbol,
                String.format("%.1f", total),
                String.format("%.1f", sentimentScore),
                String.format("%.1f", trendScore),
                String.format("%.1f", volumeScore),
                String.format("%.1f", rsiScore),
                action);

        return score;
    }

    private double calculateSentimentScore(String symbol) {
        LocalDateTime since = LocalDateTime.now().minusHours(4);
        List<SentimentAnalysis> analyses = sentimentRepository
                .findBySymbolAndCreatedAtAfterOrderByCreatedAtDesc(symbol, since);

        if (analyses.isEmpty()) return 50.0; // neutral

        long bullish = analyses.stream().filter(a -> a.getSentiment() == SentimentType.BULLISH).count();
        long bearish = analyses.stream().filter(a -> a.getSentiment() == SentimentType.BEARISH).count();
        long total = analyses.size();

        // Weighted by impact and confidence
        double weightedScore = 0;
        double totalWeight = 0;
        for (SentimentAnalysis a : analyses) {
            double weight = (a.getConfidence() / 100.0) * (a.getImpact() / 100.0);
            double sentVal = switch (a.getSentiment()) {
                case BULLISH -> 100.0;
                case BEARISH -> 0.0;
                case NEUTRAL -> 50.0;
            };
            weightedScore += sentVal * weight;
            totalWeight += weight;
        }

        return totalWeight > 0 ? weightedScore / totalWeight : 50.0;
    }

    private double calculateTrendScore(IndicatorResult ind) {
        return switch (ind.getTrend()) {
            case "STRONG_UPTREND" -> 85.0;
            case "UPTREND" -> 65.0;
            case "SIDEWAYS" -> 50.0;
            case "DOWNTREND" -> 35.0;
            case "STRONG_DOWNTREND" -> 15.0;
            default -> 50.0;
        };
    }

    private double calculateVolumeScore(IndicatorResult ind) {
        if (ind.getVolumeAvg20() <= 0) return 50.0;
        double ratio = ind.getCurrentVolume() / ind.getVolumeAvg20();

        // High volume increases conviction in both directions — neutral base, boosted by volume
        if (ratio >= 2.0) return 80.0;
        if (ratio >= 1.5) return 70.0;
        if (ratio >= 1.0) return 60.0;
        if (ratio >= 0.7) return 50.0;
        return 40.0;
    }

    private double calculateRsiScore(double rsi) {
        // Mean-reversion: buy oversold dips, avoid overbought tops
        if (rsi <= 20) return 90.0;
        if (rsi <= 30) return 75.0;
        if (rsi <= 45) return 60.0;
        if (rsi <= 55) return 50.0;
        if (rsi <= 70) return 40.0;
        if (rsi <= 80) return 25.0;
        return 10.0;
    }

    private int calculateConfidence(String symbol, IndicatorResult ind, double totalScore) {
        // Higher confidence when score is far from the neutral zone (50)
        double deviation = Math.abs(totalScore - 50.0) / 50.0;
        int baseConfidence = (int) Math.round(deviation * 100);

        // Boost confidence when multiple indicators agree
        boolean trendBullish = ind.getTrend().contains("UPTREND");
        boolean trendBearish = ind.getTrend().contains("DOWNTREND");
        boolean rsiOversold = ind.isOversold();
        boolean rsiOverbought = ind.isOverbought();
        boolean highVol = ind.isHighVolume();

        if (totalScore >= 50 && trendBullish && (rsiOversold || highVol)) baseConfidence += 15;
        if (totalScore <= 50 && trendBearish && (rsiOverbought || highVol)) baseConfidence += 15;

        // Boost when score clearly crosses an actionable threshold
        if (totalScore >= scoringProperties.getBuyThreshold()) baseConfidence += 10;
        else if (totalScore <= scoringProperties.getSellThreshold()) baseConfidence += 10;

        return Math.max(0, Math.min(100, baseConfidence));
    }
}
