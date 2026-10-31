package com.cricauction.modules.auction.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WishlistActionRequest {

    @NotNull(message = "playerId is required")
    @Schema(example = "12", description = "ID of tournament player")
    private Long playerId;

    @NotBlank(message = "action is required ('add' or 'remove')")
    @Pattern(regexp = "(?i)^(add|remove)$", message = "action must be either 'add' or 'remove'")
    @Schema(example = "add", description = "Action to perform: add or remove")
    private String action;

    @Schema(example = "75000", description = "Target maximum budget for this player")
    private Long maxBudget;

    @Schema(example = "High", description = "Priority level (e.g. High, Medium, Low)")
    private String priority;
}
