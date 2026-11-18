package com.airbnb.warroom.venue;

import com.airbnb.warroom.common.ApiException;
import com.airbnb.warroom.venue.VenueDtos.UploadResult;
import com.airbnb.warroom.venue.VenueDtos.VenueCatalogResponse;
import com.airbnb.warroom.venue.VenueDtos.VenueUploadRequest;
import com.airbnb.warroom.venue.VenueDtos.VenueView;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class VenueController {

    private final VenueService venueService;

    public VenueController(VenueService venueService) {
        this.venueService = venueService;
    }

    /** PRD 7.3.2 — venue metadata for a destination, optionally filtered by proximity to an anchor. */
    @GetMapping("/destinations/{destination}/venues")
    public Mono<VenueCatalogResponse> venues(@PathVariable String destination,
                                             @RequestParam(required = false) String near,
                                             @RequestParam(required = false) Double radiusKm) {
        Double lat = null;
        Double lng = null;
        if (near != null && !near.isBlank()) {
            String[] parts = near.split(",");
            if (parts.length != 2) {
                return Mono.error(ApiException.badRequest("near must be 'lat,lng'"));
            }
            try {
                lat = Double.parseDouble(parts[0].trim());
                lng = Double.parseDouble(parts[1].trim());
            } catch (NumberFormatException e) {
                return Mono.error(ApiException.badRequest("near must be numeric 'lat,lng'"));
            }
        }
        return venueService.findNear(destination, lat, lng, radiusKm)
                .map(vs -> new VenueCatalogResponse(destination,
                        vs.stream().map(VenueView::from).toList()));
    }

    /** PRD 6.3 — admin dataset upload (RBAC: /api/v1/admin/** requires ROLE_ADMIN). */
    @PostMapping("/admin/venues")
    public Mono<UploadResult> upload(@Valid @RequestBody VenueUploadRequest req) {
        return venueService.uploadDataset(req);
    }
}
