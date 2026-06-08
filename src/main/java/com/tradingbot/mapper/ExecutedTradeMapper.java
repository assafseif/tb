package com.tradingbot.mapper;

import com.tradingbot.dto.ExecutedTradeDto;
import com.tradingbot.entity.ExecutedTrade;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ExecutedTradeMapper {

    ExecutedTradeDto toDto(ExecutedTrade entity);

    List<ExecutedTradeDto> toDtoList(List<ExecutedTrade> entities);
}
