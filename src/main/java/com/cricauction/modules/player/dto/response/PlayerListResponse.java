package com.cricauction.modules.player.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlayerListResponse {
    private long total;
    private int page;
    private int limit;
    private List<PlayerSummaryResponse> players;
}
