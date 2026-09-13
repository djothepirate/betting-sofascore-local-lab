package com.bettingproject.sofascorelocal.adapter.persistence.live;

import com.bettingproject.sofascorelocal.domain.live.LiveCampaignData.*;
import com.bettingproject.sofascorelocal.port.ProviderCampaignGuardStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcProviderCampaignGuardStore implements ProviderCampaignGuardStore {
    private final JdbcTemplate jdbc;
    public JdbcProviderCampaignGuardStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    @Transactional(readOnly = true)
    public Guard snapshot() { return read(false); }

    @Override
    @Transactional
    public Optional<Guard> tryAcquire(UUID campaignId, Owner owner, Instant at) {
        java.util.Objects.requireNonNull(campaignId); java.util.Objects.requireNonNull(owner);
        java.util.Objects.requireNonNull(at);
        Guard current = read(true);
        if (!current.state().equals("FREE")) return Optional.empty();
        jdbc.update("""
            update provider_campaign_guard set state='OWNED',campaign_id=?,owner_instance_id=?,
                owner_process_id=?,owner_process_started_at=?,generation=generation+1,changed_at=?
            where singleton_id=1 and state='FREE'
            """, campaignId, owner.instanceId(), owner.processId(), Timestamp.from(owner.processStartedAt()), Timestamp.from(at));
        return Optional.of(read(false));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isOwned(Ownership ownership) {
        Guard current=read(false);
        return "OWNED".equals(current.state()) && matches(current, ownership);
    }

    @Override
    @Transactional
    public void requireCleanup(Ownership ownership, Instant at) {
        require(read(true), ownership);
        jdbc.update("update provider_campaign_guard set state='CLEANUP_REQUIRED',changed_at=? where singleton_id=1", Timestamp.from(at));
    }

    @Override
    @Transactional
    public void releaseAfterVerifiedCleanup(Ownership ownership, Instant at) {
        require(read(true), ownership);
        jdbc.update("""
            update provider_campaign_guard set state='FREE',campaign_id=null,owner_instance_id=null,
                owner_process_id=null,owner_process_started_at=null,changed_at=? where singleton_id=1
            """, Timestamp.from(at));
    }

    @Override
    @Transactional
    public void releaseManualOrphanAfterVerifiedCleanup(Guard expected, Instant at) {
        if (!matchesManualOrphan(read(true), expected)) throw new IllegalStateException("LIVE_CLEANUP_STATE_CHANGED");
        jdbc.update("""
            update provider_campaign_guard set state='FREE',campaign_id=null,owner_instance_id=null,
                owner_process_id=null,owner_process_started_at=null,changed_at=? where singleton_id=1
            """, Timestamp.from(at));
    }

    private Guard read(boolean lock) {
        return jdbc.queryForObject("select * from provider_campaign_guard where singleton_id=1" + (lock ? " for update" : ""),
                (rs, row) -> {
                    UUID instance = rs.getObject("owner_instance_id", UUID.class);
                    Owner owner = instance == null ? null : new Owner(instance, rs.getLong("owner_process_id"),
                            rs.getTimestamp("owner_process_started_at").toInstant());
                    return new Guard(rs.getString("state"), rs.getObject("campaign_id", UUID.class), owner,
                            rs.getLong("generation"), rs.getTimestamp("changed_at").toInstant());
                });
    }
    private static boolean matches(Guard guard, Ownership ownership) {
        return ownership != null && !guard.state().equals("FREE") && guard.owner() != null
                && guard.campaignId().equals(ownership.campaignId())
                && guard.owner().instanceId().equals(ownership.instanceId()) && guard.generation() == ownership.generation();
    }
    private static boolean matchesManualOrphan(Guard current, Guard expected) {
        return expected != null && "CLEANUP_REQUIRED".equals(current.state())
                && "CLEANUP_REQUIRED".equals(expected.state()) && current.campaignId() != null
                && current.campaignId().equals(expected.campaignId()) && current.owner() != null
                && current.owner().equals(expected.owner()) && current.generation() == expected.generation()
                && current.changedAt().equals(expected.changedAt());
    }
    private static void require(Guard guard, Ownership ownership) {
        if (!matches(guard, ownership)) throw new IllegalStateException("provider campaign ownership is stale");
    }
}
