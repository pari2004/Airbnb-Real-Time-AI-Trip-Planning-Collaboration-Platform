package com.airbnb.warroom.trip;

import com.airbnb.warroom.common.ApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** PRD 10.1: four-role RBAC enforced server-side. These assert the gate rejects, not just hides. */
class TripAccessServiceTest {

    private TripParticipant participant(String role) {
        return new TripParticipant(1L, 7L, role, false, Instant.now());
    }

    private TripAccessService withRole(String role) {
        TripParticipantRepository repo = mock(TripParticipantRepository.class);
        when(repo.find(1L, 7L)).thenReturn(role == null ? Mono.empty() : Mono.just(participant(role)));
        return new TripAccessService(repo);
    }

    @Test
    @DisplayName("Viewer cannot pin")
    void viewerCannotPin() {
        StepVerifier.create(withRole("viewer").requireCanPin(1L, 7L))
                .expectErrorMatches(e -> e instanceof ApiException && ((ApiException) e).getStatus() == 403)
                .verify();
    }

    @Test
    @DisplayName("Planner can pin but cannot moderate")
    void plannerCapabilities() {
        StepVerifier.create(withRole("planner").requireCanPin(1L, 7L))
                .expectNextCount(1).verifyComplete();
        StepVerifier.create(withRole("planner").requireLeader(1L, 7L))
                .expectErrorMatches(e -> e instanceof ApiException && ((ApiException) e).getStatus() == 403)
                .verify();
    }

    @Test
    @DisplayName("Leader can moderate")
    void leaderCanModerate() {
        StepVerifier.create(withRole("leader").requireLeader(1L, 7L))
                .expectNextCount(1).verifyComplete();
    }

    @Test
    @DisplayName("Non-member is forbidden from the room")
    void nonMemberForbidden() {
        StepVerifier.create(withRole(null).requireMember(1L, 7L))
                .expectErrorMatches(e -> e instanceof ApiException && ((ApiException) e).getStatus() == 403)
                .verify();
    }
}
