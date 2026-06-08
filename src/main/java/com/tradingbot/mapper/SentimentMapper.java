package com.tradingbot.mapper;

import com.tradingbot.dto.SentimentDto;
import com.tradingbot.entity.SentimentAnalysis;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface SentimentMapper {

    SentimentDto toDto(SentimentAnalysis entity);

    List<SentimentDto> toDtoList(List<SentimentAnalysis> entities);
}
