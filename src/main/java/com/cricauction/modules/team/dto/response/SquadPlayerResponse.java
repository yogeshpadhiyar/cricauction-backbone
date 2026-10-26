package com.cricauction.modules.team.dto.response;

import com.cricauction.modules.team.entity.SquadMember;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SquadPlayerResponse {

    private String id;
    private String name;
    private String role;
    private Long purchasePrice;

    @JsonProperty("isCaptain")
    private boolean isCaptain;

    private String photoUrl;

    public static SquadPlayerResponse fromEntity(SquadMember member) {
        return SquadPlayerResponse.builder()
                .id("ply_" + member.getId())
                .name(member.getPlayerName())
                .role(member.getPlayingRole())
                .purchasePrice(member.getPurchasePrice())
                .isCaptain(member.isCaptain())
                .photoUrl(member.getPhotoUrl())
                .build();
    }
}
