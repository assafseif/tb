package com.tradingbot.mapper;

import com.tradingbot.dto.NewsEventDto;
import com.tradingbot.entity.NewsEvent;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface NewsMapper {

    NewsEventDto toDto(NewsEvent entity);

    List<NewsEventDto> toDtoList(List<NewsEvent> entities);
}
