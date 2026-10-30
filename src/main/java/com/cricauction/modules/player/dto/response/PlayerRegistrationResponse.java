package com.cricauction.modules.player.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlayerRegistrationResponse {
    private String registrationId;
    private String status;
    private String tournamentName;
}
