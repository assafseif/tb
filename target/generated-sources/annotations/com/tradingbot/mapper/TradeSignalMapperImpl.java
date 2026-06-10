package com.tradingbot.mapper;

import com.tradingbot.dto.TradeSignalDto;
import com.tradingbot.entity.TradeSignal;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-06-10T01:10:29+0300",
    comments = "version: 1.5.5.Final, compiler: Eclipse JDT (IDE) 3.46.0.v20260407-0427, environment: Java 21.0.10 (Eclipse Adoptium)"
)
@Component
public class TradeSignalMapperImpl implements TradeSignalMapper {

    @Override
    public TradeSignalDto toDto(TradeSignal entity) {
        if ( entity == null ) {
            return null;
        }

        TradeSignalDto.TradeSignalDtoBuilder tradeSignalDto = TradeSignalDto.builder();

        tradeSignalDto.confidence( entity.getConfidence() );
        tradeSignalDto.createdAt( entity.getCreatedAt() );
        tradeSignalDto.entryPrice( entity.getEntryPrice() );
        tradeSignalDto.id( entity.getId() );
        tradeSignalDto.processedAt( entity.getProcessedAt() );
        tradeSignalDto.rsiScore( entity.getRsiScore() );
        tradeSignalDto.score( entity.getScore() );
        tradeSignalDto.sentimentScore( entity.getSentimentScore() );
        tradeSignalDto.side( entity.getSide() );
        tradeSignalDto.status( entity.getStatus() );
        tradeSignalDto.stopLoss( entity.getStopLoss() );
        tradeSignalDto.symbol( entity.getSymbol() );
        tradeSignalDto.takeProfit( entity.getTakeProfit() );
        tradeSignalDto.trendScore( entity.getTrendScore() );
        tradeSignalDto.volumeScore( entity.getVolumeScore() );

        return tradeSignalDto.build();
    }

    @Override
    public List<TradeSignalDto> toDtoList(List<TradeSignal> entities) {
        if ( entities == null ) {
            return null;
        }

        List<TradeSignalDto> list = new ArrayList<TradeSignalDto>( entities.size() );
        for ( TradeSignal tradeSignal : entities ) {
            list.add( toDto( tradeSignal ) );
        }

        return list;
    }
}
