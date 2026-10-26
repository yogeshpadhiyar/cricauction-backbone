package com.cricauction.modules.tournament.dto.response;

import com.cricauction.modules.tournament.entity.Tournament;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TournamentSummaryResponse {
    private String id;
    private String name;
    private String location;
    private LocalDate startDate;
    private LocalDate endDate;
    private int teamsCount;
    private int playersRegistered;
    private String status;

    public static TournamentSummaryResponse fromEntity(Tournament tournament, int teamsCount, int playersRegistered) {
        return TournamentSummaryResponse.builder()
                .id(tournament.getPublicId())
                .name(tournament.getName())
                .location(tournament.getLocation())
                .startDate(tournament.getStartDate())
                .endDate(tournament.getEndDate())
                .teamsCount(teamsCount > 0 ? teamsCount : (tournament.getSettings() != null ? tournament.getSettings().getTotalTeams() : 0))
                .playersRegistered(playersRegistered)
                .status(tournament.getStatus() != null ? tournament.getStatus().getValue() : "draft")
                .build();
    }
}
