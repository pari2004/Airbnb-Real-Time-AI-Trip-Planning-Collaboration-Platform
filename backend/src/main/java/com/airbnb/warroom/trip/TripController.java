package com.airbnb.warroom.trip;

import com.airbnb.warroom.auth.SecurityUtil;
import com.airbnb.warroom.common.PublicId;
import com.airbnb.warroom.trip.TripDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/trips")
public class TripController {

    private final TripService tripService;

    public TripController(TripService tripService) {
        this.tripService = tripService;
    }

    /** PRD 7.3.1 — create a Trip Room. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<CreateTripResponse> create(@Valid @RequestBody CreateTripRequest req) {
        return SecurityUtil.currentUserId()
                .flatMap(uid -> tripService.createTrip(uid, req))
                .map(CreateTripResponse::from);
    }

    @GetMapping("/{id}")
    public Mono<TripView> get(@PathVariable String id) {
        return SecurityUtil.currentUserId()
                .flatMap(uid -> tripService.getTripView(PublicId.decode(id), uid));
    }

    @PostMapping("/join")
    public Mono<TripView> join(@Valid @RequestBody JoinRequest req) {
        return SecurityUtil.currentUserId()
                .flatMap(uid -> tripService.joinByCode(uid, req.inviteCode()));
    }

    @PostMapping("/{id}/invite")
    public Mono<InviteView> invite(@PathVariable String id) {
        return SecurityUtil.currentUserId()
                .flatMap(uid -> tripService.invite(PublicId.decode(id), uid));
    }

    @PatchMapping("/{id}/settings")
    public Mono<TripView> settings(@PathVariable String id, @RequestBody SettingsRequest req) {
        return SecurityUtil.currentUserId()
                .flatMap(uid -> tripService.updateSettings(PublicId.decode(id), uid, req));
    }

    @PostMapping("/{id}/participants/role")
    public Mono<TripView> assignRole(@PathVariable String id, @Valid @RequestBody AssignRoleRequest req) {
        return SecurityUtil.currentUserId()
                .flatMap(uid -> tripService.assignRole(PublicId.decode(id), uid, req.userId(), req.role()));
    }

    @PostMapping("/{id}/transfer-leader")
    public Mono<TripView> transferLeader(@PathVariable String id, @Valid @RequestBody TransferLeaderRequest req) {
        return SecurityUtil.currentUserId()
                .flatMap(uid -> tripService.transferLeader(PublicId.decode(id), uid, req.userId()));
    }
}
