package com.tradingbot.mapper;

import com.tradingbot.dto.NewsEventDto;
import com.tradingbot.entity.NewsEvent;
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
public class NewsMapperImpl implements NewsMapper {

    @Override
    public NewsEventDto toDto(NewsEvent entity) {
        if ( entity == null ) {
            return null;
        }

        NewsEventDto.NewsEventDtoBuilder newsEventDto = NewsEventDto.builder();

        newsEventDto.categories( entity.getCategories() );
        newsEventDto.createdAt( entity.getCreatedAt() );
        newsEventDto.id( entity.getId() );
        newsEventDto.processed( entity.isProcessed() );
        newsEventDto.publishedAt( entity.getPublishedAt() );
        newsEventDto.source( entity.getSource() );
        newsEventDto.title( entity.getTitle() );

        return newsEventDto.build();
    }

    @Override
    public List<NewsEventDto> toDtoList(List<NewsEvent> entities) {
        if ( entities == null ) {
            return null;
        }

        List<NewsEventDto> list = new ArrayList<NewsEventDto>( entities.size() );
        for ( NewsEvent newsEvent : entities ) {
            list.add( toDto( newsEvent ) );
        }

        return list;
    }
}
