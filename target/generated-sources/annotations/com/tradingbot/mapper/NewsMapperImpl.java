package com.tradingbot.mapper;

import com.tradingbot.dto.NewsEventDto;
import com.tradingbot.entity.NewsEvent;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-06-09T12:22:37+0300",
    comments = "version: 1.5.5.Final, compiler: javac, environment: Java 21.0.11 (Ubuntu)"
)
@Component
public class NewsMapperImpl implements NewsMapper {

    @Override
    public NewsEventDto toDto(NewsEvent entity) {
        if ( entity == null ) {
            return null;
        }

        NewsEventDto.NewsEventDtoBuilder newsEventDto = NewsEventDto.builder();

        newsEventDto.id( entity.getId() );
        newsEventDto.title( entity.getTitle() );
        newsEventDto.content( entity.getContent() );
        newsEventDto.source( entity.getSource() );
        newsEventDto.publishedAt( entity.getPublishedAt() );
        newsEventDto.processed( entity.isProcessed() );
        newsEventDto.categories( entity.getCategories() );
        newsEventDto.createdAt( entity.getCreatedAt() );

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
