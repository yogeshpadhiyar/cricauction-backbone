package com.cricauction.modules.dashboard.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CaptainTeamDto {
    private String id;
    private String name;
    private String tournamentId;
    private String tournamentName;
    private BigDecimal totalBudget;
    private BigDecimal remainingBudget;
    private String budgetFormatted;
    private long squadSize;
    private Integer maxSquad;
}
