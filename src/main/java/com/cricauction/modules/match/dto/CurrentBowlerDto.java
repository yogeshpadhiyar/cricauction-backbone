package com.cricauction.modules.match.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CurrentBowlerDto {
    private String name;
    private Object overs;
    private Integer wickets;
    private Integer runs;
}

