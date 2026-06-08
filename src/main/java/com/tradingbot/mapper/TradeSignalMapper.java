package com.tradingbot.mapper;

import com.tradingbot.dto.TradeSignalDto;
import com.tradingbot.entity.TradeSignal;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface TradeSignalMapper {

    TradeSignalDto toDto(TradeSignal entity);

    List<TradeSignalDto> toDtoList(List<TradeSignal> entities);
}
