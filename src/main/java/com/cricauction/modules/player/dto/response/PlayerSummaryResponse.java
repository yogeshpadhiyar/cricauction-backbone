package com.cricauction.modules.player.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlayerSummaryResponse {
    private Long id;
    private String name;
    private String role;
    private String basePrice;
    private String status;
    private String mobile;
    private String photo;
}
