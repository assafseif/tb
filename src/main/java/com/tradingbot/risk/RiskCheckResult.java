package com.tradingbot.risk;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskCheckResult {
    private boolean approved;
    private String reason;
    private double positionSize;
    private double riskAmount;

    public static RiskCheckResult approved(double positionSize, double riskAmount) {
        return RiskCheckResult.builder()
                .approved(true)
                .reason("Risk checks passed")
                .positionSize(positionSize)
                .riskAmount(riskAmount)
                .build();
    }

    public static RiskCheckResult rejected(String reason) {
        return RiskCheckResult.builder()
                .approved(false)
                .reason(reason)
                .positionSize(0)
                .riskAmount(0)
                .build();
    }
}
