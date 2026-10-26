package com.cricauction.modules.tournament.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TournamentSettingsRequest {

    @Schema(example = "10")
    private Integer teams;

    @Schema(example = "12")
    private Integer squadMinimum;

    @Schema(example = "15")
    private Integer squadMaximum;

    @Schema(example = "1000000")
    private Long budget;

    @Schema(example = "20000")
    private Long basePrice;

    @Schema(example = "10000")
    private Long bidIncrement;

    @Schema(example = "10")
    private Integer bidTimer;

    @Schema(example = "false")
    private Boolean autoStartNext;

    @Schema(example = "20")
    private Integer matches;

    @Schema(example = "3")
    private Integer matchDuration;

    @Schema(example = "true")
    private Boolean allowPlayersToWithdraw;
}
