package com.tradingbot.mapper;

import com.tradingbot.dto.ExecutedTradeDto;
import com.tradingbot.entity.ExecutedTrade;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-06-09T15:12:06+0300",
    comments = "version: 1.5.5.Final, compiler: javac, environment: Java 21.0.11 (Ubuntu)"
)
@Component
public class ExecutedTradeMapperImpl implements ExecutedTradeMapper {

    @Override
    public ExecutedTradeDto toDto(ExecutedTrade entity) {
        if ( entity == null ) {
            return null;
        }

        ExecutedTradeDto.ExecutedTradeDtoBuilder executedTradeDto = ExecutedTradeDto.builder();

        executedTradeDto.id( entity.getId() );
        executedTradeDto.symbol( entity.getSymbol() );
        executedTradeDto.side( entity.getSide() );
        executedTradeDto.quantity( entity.getQuantity() );
        executedTradeDto.entryPrice( entity.getEntryPrice() );
        executedTradeDto.stopLoss( entity.getStopLoss() );
        executedTradeDto.takeProfit( entity.getTakeProfit() );
        executedTradeDto.status( entity.getStatus() );
        executedTradeDto.binanceOrderId( entity.getBinanceOrderId() );
        executedTradeDto.signalId( entity.getSignalId() );
        executedTradeDto.paperTrade( entity.isPaperTrade() );
        executedTradeDto.closePrice( entity.getClosePrice() );
        executedTradeDto.realizedPnl( entity.getRealizedPnl() );
        executedTradeDto.errorMessage( entity.getErrorMessage() );
        executedTradeDto.createdAt( entity.getCreatedAt() );
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
