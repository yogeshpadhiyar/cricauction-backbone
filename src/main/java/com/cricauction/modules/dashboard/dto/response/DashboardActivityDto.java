package com.cricauction.modules.dashboard.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardActivityDto {
    private String id;
    private String type;
    private String title;
    private String subtitle;
    private Instant timestamp;
}
