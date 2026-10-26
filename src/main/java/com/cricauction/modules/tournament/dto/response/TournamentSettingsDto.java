package com.cricauction.modules.tournament.dto.response;

import com.cricauction.modules.tournament.entity.TournamentSettings;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TournamentSettingsDto {
    private int teams;
    private int squadMinimum;
    private int squadMaximum;
    private long budget;
    private long basePrice;
    private long bidIncrement;
    private int bidTimer;
    private boolean autoStartNext;
    private int matches;
    private int matchDuration;
    private boolean allowPlayersToWithdraw;

    public static TournamentSettingsDto fromEntity(TournamentSettings entity) {
        if (entity == null) {
            return null;
        }
        return TournamentSettingsDto.builder()
                .teams(entity.getTotalTeams())
                .squadMinimum(entity.getSquadMinimum())
                .squadMaximum(entity.getSquadMaximum())
                .budget(entity.getTeamBudget())
                .basePrice(entity.getDefaultBasePrice())
                .bidIncrement(entity.getBidIncrement())
                .bidTimer(entity.getBidTimerSeconds())
                .autoStartNext(entity.isAutoStartNext())
                .matches(entity.getMatches())
                .matchDuration(entity.getMatchDurationHours())
                .allowPlayersToWithdraw(entity.isAllowPlayersToWithdraw())
                .build();
    }
}
