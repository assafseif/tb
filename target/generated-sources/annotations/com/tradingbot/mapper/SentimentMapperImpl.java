package com.tradingbot.mapper;

import com.tradingbot.dto.SentimentDto;
import com.tradingbot.entity.SentimentAnalysis;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-06-09T12:22:36+0300",
    comments = "version: 1.5.5.Final, compiler: javac, environment: Java 21.0.11 (Ubuntu)"
)
@Component
public class SentimentMapperImpl implements SentimentMapper {

    @Override
    public SentimentDto toDto(SentimentAnalysis entity) {
        if ( entity == null ) {
            return null;
        }

        SentimentDto.SentimentDtoBuilder sentimentDto = SentimentDto.builder();

        sentimentDto.id( entity.getId() );
        sentimentDto.newsId( entity.getNewsId() );
        sentimentDto.symbol( entity.getSymbol() );
        sentimentDto.sentiment( entity.getSentiment() );
        sentimentDto.confidence( entity.getConfidence() );
        sentimentDto.impact( entity.getImpact() );
        sentimentDto.reason( entity.getReason() );
        sentimentDto.createdAt( entity.getCreatedAt() );

        return sentimentDto.build();
    }

    @Override
    public List<SentimentDto> toDtoList(List<SentimentAnalysis> entities) {
        if ( entities == null ) {
            return null;
        }

        List<SentimentDto> list = new ArrayList<SentimentDto>( entities.size() );
        for ( SentimentAnalysis sentimentAnalysis : entities ) {
            list.add( toDto( sentimentAnalysis ) );
        }

        return list;
    }
}
