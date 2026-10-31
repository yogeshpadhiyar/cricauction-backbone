package com.cricauction.modules.auction.controller;

import com.cricauction.common.api.ApiResponse;
import com.cricauction.modules.auction.dto.request.AcceleratedRoundRequest;
import com.cricauction.modules.auction.dto.request.NextPlayerRequest;
import com.cricauction.modules.auction.dto.request.PlaceBidRequest;
import com.cricauction.modules.auction.dto.request.StartAuctionRequest;
import com.cricauction.modules.auction.dto.request.WishlistActionRequest;
import com.cricauction.modules.auction.dto.response.*;
import com.cricauction.modules.auction.service.AuctionService;
import com.cricauction.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller exposing all 13 REST Endpoints for Module 7: Live Auction Engine.
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/tournaments/{tournamentId}/auction")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
@Tag(name = "Module 7: Live Auction Engine", description = "Endpoints for managing real-time tournament auctions, bids, hammers, and wishlists")
public class LiveAuctionController {

    private final AuctionService auctionService;

    @PostMapping("/start")
    @Operation(summary = "API 41: Initialize & Open Auction Room", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<AuctionStartResponse>> startAuction(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable("tournamentId") @NotBlank @Size(max = 100) String tournamentId,
            @Valid @RequestBody(required = false) StartAuctionRequest request
    ) {
        AuctionStartResponse data = auctionService.initializeAndStartAuction(currentUser, tournamentId, request);
        return ResponseEntity.ok(ApiResponse.success("Auction room is now LIVE", data));
    }

    @PostMapping("/pause")
    @Operation(summary = "API 42: Pause Live Auction", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<Void>> pauseAuction(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable("tournamentId") @NotBlank @Size(max = 100) String tournamentId
    ) {
        auctionService.pauseAuction(currentUser, tournamentId);
        return ResponseEntity.ok(ApiResponse.success("Auction paused", null));
    }

    @PostMapping("/resume")
    @Operation(summary = "API 43: Resume Live Auction", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<Void>> resumeAuction(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable("tournamentId") @NotBlank @Size(max = 100) String tournamentId
    ) {
        auctionService.resumeAuction(currentUser, tournamentId);
        return ResponseEntity.ok(ApiResponse.success("Auction resumed", null));
    }

    @PostMapping("/next-player")
    @Operation(summary = "API 44: Call Next Player to Hammer", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<CurrentPlayerDto>> callNextPlayer(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable("tournamentId") @NotBlank @Size(max = 100) String tournamentId,
            @Valid @RequestBody(required = false) NextPlayerRequest request
    ) {
        CurrentPlayerDto data = auctionService.callNextPlayer(currentUser, tournamentId, request);
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    @PostMapping("/bid")
    @Operation(summary = "API 45: Place Bid (Captain)", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<BidResponse>> placeBid(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable("tournamentId") @NotBlank @Size(max = 100) String tournamentId,
            @Valid @RequestBody PlaceBidRequest request
    ) {
        BidResponse data = auctionService.placeBid(currentUser, tournamentId, request);
        return ResponseEntity.ok(ApiResponse.success("Bid accepted", data));
    }

    @PostMapping("/sell")
    @Operation(summary = "API 46: Sell Player to Leading Team ('Sold')", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<AuctionSellResponse>> sellPlayer(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable("tournamentId") @NotBlank @Size(max = 100) String tournamentId
    ) {
        AuctionSellResponse data = auctionService.sellPlayer(currentUser, tournamentId);
        return ResponseEntity.ok(ApiResponse.success("Player SOLD", data));
    }

    @PostMapping("/pass")
    @Operation(summary = "API 47: Pass Player as Unsold", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<AuctionPassResponse>> passPlayer(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable("tournamentId") @NotBlank @Size(max = 100) String tournamentId
    ) {
        AuctionPassResponse data = auctionService.passPlayer(currentUser, tournamentId);
        return ResponseEntity.ok(ApiResponse.success("Player marked UNSOLD", data));
    }

    @PostMapping("/undo")
    @Operation(summary = "API 48: Undo Last Bid", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<AuctionUndoResponse>> undoLastBid(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable("tournamentId") @NotBlank @Size(max = 100) String tournamentId
    ) {
        AuctionUndoResponse data = auctionService.undoLastBid(currentUser, tournamentId);
        return ResponseEntity.ok(ApiResponse.success("Last bid reverted", data));
    }

    @GetMapping("/state")
    @Operation(summary = "API 49: Get Current Auction Room State", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<AuctionStateResponse>> getAuctionState(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable("tournamentId") @NotBlank @Size(max = 100) String tournamentId
    ) {
        AuctionStateResponse data = auctionService.getAuctionState(currentUser, tournamentId);
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    @GetMapping("/history")
    @Operation(summary = "API 50: Get Auction Transaction & Sold History", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<List<AuctionHistoryItemDto>>> getAuctionHistory(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable("tournamentId") @NotBlank @Size(max = 100) String tournamentId
    ) {
        List<AuctionHistoryItemDto> data = auctionService.getAuctionHistory(currentUser, tournamentId);
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    @PostMapping("/accelerated-round")
    @Operation(summary = "API 51: Start Accelerated / Unsold Re-entry Round", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<Void>> startAcceleratedRound(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable("tournamentId") @NotBlank @Size(max = 100) String tournamentId,
            @Valid @RequestBody(required = false) AcceleratedRoundRequest request
    ) {
        int count = auctionService.startAcceleratedRound(currentUser, tournamentId, request);
        return ResponseEntity.ok(ApiResponse.success("Accelerated round prepared successfully", null));
    }

    @GetMapping("/wishlist")
    @Operation(summary = "API 52: Captain Views Target Player Wishlist", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<List<WishlistItemDto>>> getWishlist(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable("tournamentId") @NotBlank @Size(max = 100) String tournamentId
    ) {
        List<WishlistItemDto> data = auctionService.getCaptainWishlist(currentUser, tournamentId);
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    @PostMapping("/wishlist")
    @Operation(summary = "API 53: Captain Adds / Removes Player from Wishlist", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<Void>> updateWishlist(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable("tournamentId") @NotBlank @Size(max = 100) String tournamentId,
            @Valid @RequestBody WishlistActionRequest request
    ) {
        auctionService.updateCaptainWishlist(currentUser, tournamentId, request);
        return ResponseEntity.ok(ApiResponse.success("Wishlist updated", null));
    }
}
