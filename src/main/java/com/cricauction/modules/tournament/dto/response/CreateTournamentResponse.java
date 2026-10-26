package com.cricauction.modules.tournament.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTournamentResponse {
    private String id;
    private String slug;
    private int step;
    private Instant createdAt;
}
