package com.tradingbot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class AiResponseDto {
    @JsonProperty("model")
    private String model;

    @JsonProperty("response")
    private String response;
}
