package com.cricauction.modules.tournament.dto.response;

import com.cricauction.modules.tournament.entity.Tournament;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
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

    public static PublicTournamentResponse fromEntity(Tournament tournament, int registeredCount) {
        int capacity = 150;
        if (tournament.getSettings() != null) {
            capacity = tournament.getSettings().getTotalTeams() * tournament.getSettings().getSquadMaximum();
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
                .build();
    }
}
