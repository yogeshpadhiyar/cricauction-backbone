package com.cricauction.modules.tournament.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
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
    @Positive(message = "Teams must be a positive integer")
    private Integer teams;

    @Schema(example = "12")
    @Positive(message = "Squad minimum must be positive")
    private Integer squadMinimum;

    @Schema(example = "15")
    @Positive(message = "Squad maximum must be positive")
    private Integer squadMaximum;

    @Schema(example = "1000000")
    @PositiveOrZero(message = "Budget cannot be negative")
    private Long budget;

    @Schema(example = "20000")
    @PositiveOrZero(message = "Base price cannot be negative")
    private Long basePrice;

    @Schema(example = "10000")
    @PositiveOrZero(message = "Bid increment cannot be negative")
    private Long bidIncrement;

    @Schema(example = "10")
    @Positive(message = "Bid timer must be positive")
    private Integer bidTimer;

    @Schema(example = "false")
    private Boolean autoStartNext;

    @Schema(example = "20")
    @PositiveOrZero(message = "Matches cannot be negative")
    private Integer matches;

    @Schema(example = "3")
    @Positive(message = "Match duration must be positive")
    private Integer matchDuration;

    @Schema(example = "true")
    private Boolean allowPlayersToWithdraw;

    @Schema(example = "INVITE_ONLY", description = "Captain joining behavior: INVITE_ONLY (default) or BOTH (supports both invites and open captain self-registration)")
    @Size(max = 30, message = "Captain registration mode cannot exceed 30 characters")
    private String captainRegistrationMode;

    @Schema(example = "ORGANIZER_APPROVAL", description = "Captain approval behavior: ORGANIZER_APPROVAL (default, organizer manually approves applicants) or AUTO (auto-approve upon registration)")
    @Size(max = 30, message = "Captain approval mode cannot exceed 30 characters")
    private String captainApprovalMode;

    @Schema(example = "ORGANIZER_APPROVAL", description = "Player approval behavior: ORGANIZER_APPROVAL (default, organizer manually approves applicants) or AUTO_APPROVAL (auto-approve upon registration)")
    @Size(max = 30, message = "Player approval mode cannot exceed 30 characters")
    private String playerApprovalMode;
}
