package com.cricauction.modules.team.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InviteCaptainRequest {

    @Builder.Default
    private String channel = "whatsapp";
}
