package com.cricauction.modules.auction.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuctionEvent<T> {
    private String eventType;
    private String tournamentId;
    private T data;
    @Builder.Default
    private Instant timestamp = Instant.now();
}
