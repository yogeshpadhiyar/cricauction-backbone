package com.cricauction.modules.tournament.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublishTournamentResponse {
    private String status;
    private String registrationLink;
    private String captainInviteLink;
}
