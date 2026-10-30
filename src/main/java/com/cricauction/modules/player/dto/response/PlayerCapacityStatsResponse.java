package com.cricauction.modules.player.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlayerCapacityStatsResponse {
    private int capacity;
    private long registered;
    private long pending;
    private long approved;
    private int percentageFilled;

    @JsonProperty("isOpen")
    private boolean isOpen;

    private RoleBreakdownResponse roleBreakdown;
}
