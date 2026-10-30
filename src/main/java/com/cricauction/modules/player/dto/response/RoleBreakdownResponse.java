package com.cricauction.modules.player.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleBreakdownResponse {
    private long batsmen;
    private long bowlers;
    private long allRounders;
    private long wicketKeepers;
}
