package com.cricauction.modules.match.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecordBallRequest {

    private Integer innings;

    @NotNull(message = "Over number is required")
    @Min(value = 0, message = "Over number cannot be negative")
    @Max(value = 100, message = "Over number cannot exceed 100")
    private Integer over;

    @NotNull(message = "Ball number is required")
    @Min(value = 1, message = "Ball number must be at least 1")
    @Max(value = 10, message = "Ball number cannot exceed 10")
    private Integer ball;

    @NotNull(message = "Runs cannot be null")
    @Min(value = 0, message = "Runs cannot be negative")
    @Max(value = 7, message = "Runs cannot exceed 7 in a single ball")
    @Builder.Default
    private Integer runs = 0;

    @JsonProperty("isWicket")
    @Builder.Default
    private Boolean isWicket = false;

    private String wicketType;

    @Min(value = 0, message = "Extra runs cannot be negative")
    @Max(value = 10, message = "Extra runs cannot exceed 10")
    @Builder.Default
    private Integer extraRuns = 0;

    private String extraType;

    private String batsmanId;

    private String batsmanName;

    private String bowlerId;

    private String bowlerName;
}

