package com.cricauction.modules.auction.service;

import com.cricauction.modules.auction.dto.request.*;
import com.cricauction.modules.auction.dto.response.*;
import com.cricauction.security.UserPrincipal;

import java.util.List;

public interface AuctionService {

    AuctionStartResponse initializeAndStartAuction(UserPrincipal currentUser, String tournamentId, StartAuctionRequest request);

    default AuctionStartResponse initializeAndStartAuction(UserPrincipal currentUser, String tournamentId) {
        return initializeAndStartAuction(currentUser, tournamentId, null);
    }

    void pauseAuction(UserPrincipal currentUser, String tournamentId);

    void resumeAuction(UserPrincipal currentUser, String tournamentId);

    CurrentPlayerDto callNextPlayer(UserPrincipal currentUser, String tournamentId, NextPlayerRequest request);

    BidResponse placeBid(UserPrincipal currentUser, String tournamentId, PlaceBidRequest request);

    AuctionSellResponse sellPlayer(UserPrincipal currentUser, String tournamentId);

    AuctionPassResponse passPlayer(UserPrincipal currentUser, String tournamentId);

    AuctionUndoResponse undoLastBid(UserPrincipal currentUser, String tournamentId);

    AuctionStateResponse getAuctionState(UserPrincipal currentUser, String tournamentId);

    List<AuctionHistoryItemDto> getAuctionHistory(UserPrincipal currentUser, String tournamentId);

    int startAcceleratedRound(UserPrincipal currentUser, String tournamentId, AcceleratedRoundRequest request);

    List<WishlistItemDto> getCaptainWishlist(UserPrincipal currentUser, String tournamentId);

    void updateCaptainWishlist(UserPrincipal currentUser, String tournamentId, WishlistActionRequest request);
}
