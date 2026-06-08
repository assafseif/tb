package com.tradingbot.mapper;

import com.tradingbot.dto.TradeSignalDto;
import com.tradingbot.entity.TradeSignal;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-06-08T14:17:49+0300",
    comments = "version: 1.5.5.Final, compiler: javac, environment: Java 21.0.11 (Ubuntu)"
)
@Component
public class TradeSignalMapperImpl implements TradeSignalMapper {

    @Override
    public TradeSignalDto toDto(TradeSignal entity) {
        if ( entity == null ) {
            return null;
        }

        TradeSignalDto.TradeSignalDtoBuilder tradeSignalDto = TradeSignalDto.builder();

        tradeSignalDto.id( entity.getId() );
        tradeSignalDto.symbol( entity.getSymbol() );
        tradeSignalDto.side( entity.getSide() );
        tradeSignalDto.score( entity.getScore() );
        tradeSignalDto.entryPrice( entity.getEntryPrice() );
        tradeSignalDto.stopLoss( entity.getStopLoss() );
        tradeSignalDto.takeProfit( entity.getTakeProfit() );
        tradeSignalDto.confidence( entity.getConfidence() );
        tradeSignalDto.status( entity.getStatus() );
        tradeSignalDto.sentimentScore( entity.getSentimentScore() );
        tradeSignalDto.trendScore( entity.getTrendScore() );
        tradeSignalDto.volumeScore( entity.getVolumeScore() );
        tradeSignalDto.rsiScore( entity.getRsiScore() );
        tradeSignalDto.createdAt( entity.getCreatedAt() );
        tradeSignalDto.processedAt( entity.getProcessedAt() );

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
