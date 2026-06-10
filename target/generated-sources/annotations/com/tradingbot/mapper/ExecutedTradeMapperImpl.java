package com.tradingbot.mapper;

import com.tradingbot.dto.ExecutedTradeDto;
import com.tradingbot.entity.ExecutedTrade;
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
public class ExecutedTradeMapperImpl implements ExecutedTradeMapper {

    @Override
    public ExecutedTradeDto toDto(ExecutedTrade entity) {
        if ( entity == null ) {
            return null;
        }

        ExecutedTradeDto.ExecutedTradeDtoBuilder executedTradeDto = ExecutedTradeDto.builder();

        executedTradeDto.binanceOrderId( entity.getBinanceOrderId() );
        executedTradeDto.closePrice( entity.getClosePrice() );
        executedTradeDto.createdAt( entity.getCreatedAt() );
        executedTradeDto.entryPrice( entity.getEntryPrice() );
        executedTradeDto.errorMessage( entity.getErrorMessage() );
        executedTradeDto.id( entity.getId() );
        executedTradeDto.paperTrade( entity.isPaperTrade() );
        executedTradeDto.quantity( entity.getQuantity() );
        executedTradeDto.realizedPnl( entity.getRealizedPnl() );
        executedTradeDto.side( entity.getSide() );
        executedTradeDto.signalId( entity.getSignalId() );
        executedTradeDto.status( entity.getStatus() );
        executedTradeDto.stopLoss( entity.getStopLoss() );
        executedTradeDto.symbol( entity.getSymbol() );
        executedTradeDto.takeProfit( entity.getTakeProfit() );
        executedTradeDto.updatedAt( entity.getUpdatedAt() );

        return executedTradeDto.build();
    }

    @Override
    public List<ExecutedTradeDto> toDtoList(List<ExecutedTrade> entities) {
        if ( entities == null ) {
            return null;
        }

        List<ExecutedTradeDto> list = new ArrayList<ExecutedTradeDto>( entities.size() );
        for ( ExecutedTrade executedTrade : entities ) {
            list.add( toDto( executedTrade ) );
        }

        return list;
    }
}
