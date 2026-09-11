package com.bettingproject.sofascorelocal.application.live;

import com.bettingproject.sofascorelocal.application.network.playwright.*;
import com.bettingproject.sofascorelocal.domain.live.LiveJ4ControlFacts;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** One explicitly opened context for exactly the four existing event endpoints. */
public final class LiveProviderSession implements AutoCloseable {
    public static final Set<SofascoreEndpointType> ENDPOINTS = Set.of(
            SofascoreEndpointType.EVENT_DETAILS, SofascoreEndpointType.EVENT_STATISTICS,
            SofascoreEndpointType.EVENT_INCIDENTS, SofascoreEndpointType.EVENT_LINEUPS);
    private final PlaywrightProviderCampaign campaign;
    private final boolean conditionalRevalidation;
    /**
     * Ephemeral, campaign-local validators.  They are deliberately neither read from nor written
     * to the durable payload cache: a fresh Playwright context starts with no conditional state.
     */
    private final Map<ConditionalKey, CachedValidator> validators = new ConcurrentHashMap<>();
    /** The exact validator supplied on an in-flight request, used to reject an uncorrelated 304. */
    private final Map<ConditionalKey, PlaywrightProviderEntityTag> dispatchedValidators = new ConcurrentHashMap<>();

    public LiveProviderSession(PlaywrightProviderCampaignFactory factory, UUID campaignId) {
        this(factory, campaignId, "live-v3");
    }
    public LiveProviderSession(PlaywrightProviderCampaignFactory factory, UUID campaignId, String policyVersion) {
        conditionalRevalidation = "live-v9".equals(policyVersion);
        campaign = switch (policyVersion) {
            case "live-v9" -> factory.openLiveGroupedV9(campaignId, ENDPOINTS);
            case "live-v8" -> factory.openLiveGroupedV8(campaignId, ENDPOINTS);
            case "live-v7" -> factory.openLiveGroupedV7(campaignId, ENDPOINTS);
            case "live-v6" -> factory.openLiveGroupedV6(campaignId, ENDPOINTS);
            case "live-v5" -> factory.openLiveGroupedV5(campaignId, ENDPOINTS);
            case "live-v4" -> factory.openLiveGrouped(campaignId, ENDPOINTS);
            case null, default -> factory.open(campaignId, ENDPOINTS);
        };
    }
    public PlaywrightProviderResponse execute(long providerId, SofascoreEndpointType endpoint,
                                               PlaywrightDispatchAdmission admission) {
        return campaign.execute(request(providerId, endpoint), admission);
    }
    public PlaywrightProviderResponse executeGrouped(long providerId, SofascoreEndpointType endpoint,
            LiveProviderDispatchGroup group, PlaywrightDispatchAdmission admission) {
        return campaign.executeGrouped(request(providerId, endpoint), group, admission);
    }

    /**
     * Returns the exact previously accepted facts only after a correlated V9 304.  No provider
     * response body, raw snapshot, normalized observation, or persisted cache entry is created by
     * this method.  The caller must publish its ledger result before {@link #acceptNotModified}.
     */
    Optional<RevalidatedExchange> revalidated(long providerId, SofascoreEndpointType endpoint,
                                              PlaywrightProviderResponse response) {
        Objects.requireNonNull(response, "response");
        ConditionalKey key = key(providerId, endpoint);
        PlaywrightProviderEntityTag sent = dispatchedValidators.remove(key);
        if (!conditionalRevalidation || response.httpStatus() != 304 || sent == null) {
            return Optional.empty();
        }
        CachedValidator cached = validators.get(key);
        if (cached == null || !cached.entityTag().equals(sent)) {
            return Optional.empty();
        }
        return Optional.of(new RevalidatedExchange(key, cached, response.entityTag()));
    }

    /**
     * Records a validator only after its 2xx response was parsed and its normalized result was
     * committed.  A 2xx response without an entity tag deliberately removes the prior validator.
     */
    void retainParsed(long providerId, SofascoreEndpointType endpoint,
                      PlaywrightProviderResponse response, ScheduleFacts facts) {
        Objects.requireNonNull(response, "response");
        Objects.requireNonNull(facts, "facts");
        ConditionalKey key = key(providerId, endpoint);
        dispatchedValidators.remove(key);
        if (!conditionalRevalidation || response.httpStatus() < 200 || response.httpStatus() >= 300
                || response.entityTag().isEmpty()) {
            validators.remove(key);
            return;
        }
        validators.put(key, new CachedValidator(response.entityTag().orElseThrow(), facts));
    }

    /**
     * Commits the harmless in-memory validator refresh from a correlated 304 only after the
     * caller has appended the corresponding non-success ledger result.
     */
    void acceptNotModified(RevalidatedExchange exchange) {
        Objects.requireNonNull(exchange, "exchange");
        if (!conditionalRevalidation) {
            throw new IllegalStateException("conditional revalidation is unavailable for this live policy");
        }
        CachedValidator current = validators.get(exchange.key);
        if (current == null || !current.entityTag().equals(exchange.cached.entityTag())) {
            throw new IllegalStateException("conditional validator changed before publication");
        }
        PlaywrightProviderEntityTag validator = exchange.responseEntityTag.orElse(current.entityTag());
        validators.put(exchange.key, new CachedValidator(validator, current.facts()));
    }

    /** Fails closed: no uncommitted or failed exchange may be used to condition a later request. */
    void discardConditionalState(long providerId, SofascoreEndpointType endpoint) {
        ConditionalKey key = key(providerId, endpoint);
        dispatchedValidators.remove(key);
        validators.remove(key);
    }

    private PlaywrightProviderRequest request(long providerId, SofascoreEndpointType endpoint) {
        ConditionalKey key = key(providerId, endpoint);
        PlaywrightProviderRequest request = new PlaywrightProviderRequest(endpoint, null, 0, 0, providerId);
        if (!conditionalRevalidation) {
            return request;
        }
        CachedValidator cached = validators.get(key);
        if (cached == null) {
            dispatchedValidators.remove(key);
            return request;
        }
        dispatchedValidators.put(key, cached.entityTag());
        return request.withIfNoneMatch(cached.entityTag());
    }

    private static ConditionalKey key(long providerId, SofascoreEndpointType endpoint) {
        if (providerId < 1 || !ENDPOINTS.contains(endpoint)) {
            throw new IllegalArgumentException("endpoint outside live scope");
        }
        return new ConditionalKey(providerId, endpoint);
    }

    @Override public void close() {
        dispatchedValidators.clear();
        validators.clear();
        campaign.close();
    }

    /** Facts already accepted from the exact endpoint in this active local campaign. */
    record ScheduleFacts(String sportStatus, Map<String, Boolean> signals, Instant scheduledKickoff,
                         Boolean lineupsConfirmed, LiveJ4ControlFacts j4Controls) {
        public ScheduleFacts {
            signals = Map.copyOf(Objects.requireNonNull(signals, "signals"));
        }
    }

    /** Opaque proof that a 304 was correlated with the exact volatile validator that was sent. */
    static final class RevalidatedExchange {
        private final ConditionalKey key;
        private final CachedValidator cached;
        private final Optional<PlaywrightProviderEntityTag> responseEntityTag;

        private RevalidatedExchange(ConditionalKey key, CachedValidator cached,
                                    Optional<PlaywrightProviderEntityTag> responseEntityTag) {
            this.key = Objects.requireNonNull(key, "key");
            this.cached = Objects.requireNonNull(cached, "cached");
            this.responseEntityTag = Objects.requireNonNull(responseEntityTag, "responseEntityTag");
        }

        ScheduleFacts facts() {
            return cached.facts();
        }
    }

    private record ConditionalKey(long providerId, SofascoreEndpointType endpoint) { }
    private record CachedValidator(PlaywrightProviderEntityTag entityTag, ScheduleFacts facts) {
        private CachedValidator {
            Objects.requireNonNull(entityTag, "entityTag");
            Objects.requireNonNull(facts, "facts");
        }
    }
}
