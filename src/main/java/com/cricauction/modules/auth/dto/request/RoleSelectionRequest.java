package com.cricauction.modules.auth.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleSelectionRequest {

    /**
     * Single role string (e.g. "organizer", "captain", "player") for adding or updating.
     */
    private String role;

    /**
     * Multiple roles list (e.g. ["PLAYER", "CAPTAIN", "ORGANIZER"]).
     */
    private List<String> roles;
}
