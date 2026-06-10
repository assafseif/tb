package com.tradingbot.mapper;

import com.tradingbot.dto.SentimentDto;
import com.tradingbot.entity.SentimentAnalysis;
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
public class SentimentMapperImpl implements SentimentMapper {

    @Override
    public SentimentDto toDto(SentimentAnalysis entity) {
        if ( entity == null ) {
            return null;
        }

        SentimentDto.SentimentDtoBuilder sentimentDto = SentimentDto.builder();

        sentimentDto.confidence( entity.getConfidence() );
        sentimentDto.createdAt( entity.getCreatedAt() );
        sentimentDto.id( entity.getId() );
        sentimentDto.impact( entity.getImpact() );
        sentimentDto.newsId( entity.getNewsId() );
        sentimentDto.reason( entity.getReason() );
        sentimentDto.sentiment( entity.getSentiment() );
        sentimentDto.symbol( entity.getSymbol() );

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
