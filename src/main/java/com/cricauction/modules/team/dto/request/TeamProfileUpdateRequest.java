package com.cricauction.modules.team.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamProfileUpdateRequest {

    private String name;
    private String shortCode;
    private String logoUrl;
}
