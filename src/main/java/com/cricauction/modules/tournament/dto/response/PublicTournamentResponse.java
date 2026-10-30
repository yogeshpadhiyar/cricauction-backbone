package com.cricauction.modules.tournament.dto.response;

import com.cricauction.modules.tournament.entity.Tournament;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicTournamentResponse {
    private String id;
    private String name;
    private String location;
    private String ground;
    private String ballType;
    private String format;
    private int capacity;
    private int registered;
    private boolean registrationOpen;
    private String auctionDate;
    private String captainRegistrationMode;
    private String captainApprovalMode;
    private String playerApprovalMode;

    public static PublicTournamentResponse fromEntity(Tournament tournament, int registeredCount) {
        int capacity = 150;
        String capMode = "INVITE_ONLY";
        String appMode = "ORGANIZER_APPROVAL";
        String plyAppMode = "ORGANIZER_APPROVAL";
        if (tournament.getSettings() != null) {
            capacity = tournament.getSettings().getTotalTeams() * tournament.getSettings().getSquadMaximum();
            if (tournament.getSettings().getCaptainRegistrationMode() != null) {
                capMode = tournament.getSettings().getCaptainRegistrationMode();
            }
            if (tournament.getSettings().getCaptainApprovalMode() != null) {
                appMode = tournament.getSettings().getCaptainApprovalMode();
            }
            if (tournament.getSettings().getPlayerApprovalMode() != null) {
                plyAppMode = tournament.getSettings().getPlayerApprovalMode();
            }
        }

        LocalDate today = LocalDate.now();
        boolean isOpen = true;
        if (tournament.getRegistrationStartDate() != null && today.isBefore(tournament.getRegistrationStartDate())) {
            isOpen = false;
        }
        if (tournament.getRegistrationEndDate() != null && today.isAfter(tournament.getRegistrationEndDate())) {
            isOpen = false;
        }

        String auctionDateIso = null;
        if (tournament.getAuctionDate() != null) {
            if (tournament.getAuctionTime() != null && !tournament.getAuctionTime().isBlank()) {
                auctionDateIso = tournament.getAuctionDate().toString() + "T" + tournament.getAuctionTime() + ":00Z";
            } else {
                auctionDateIso = tournament.getAuctionDate().toString() + "T00:00:00Z";
            }
        }

        return PublicTournamentResponse.builder()
                .id(tournament.getPublicId())
                .name(tournament.getName())
                .location(tournament.getLocation())
                .ground(tournament.getGround())
                .ballType(tournament.getBallType())
                .format(tournament.getFormat())
                .capacity(capacity)
                .registered(registeredCount)
                .registrationOpen(isOpen)
                .auctionDate(auctionDateIso)
                .captainRegistrationMode(capMode)
                .captainApprovalMode(appMode)
                .playerApprovalMode(plyAppMode)
                .build();
    }
}
