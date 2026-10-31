package com.cricauction.modules.match.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CurrentBatsmanDto {
    private String name;
    private Integer runs;
    private Integer balls;
}

