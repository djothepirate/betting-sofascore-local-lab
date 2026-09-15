package com.bettingproject.sofascorelocal.adapter.persistence;

import com.bettingproject.sofascorelocal.domain.live.LiveCampaignData.Ownership;
import com.bettingproject.sofascorelocal.port.J3LivePauseStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Repository
public class JdbcJ3LivePauseStore implements J3LivePauseStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public JdbcJ3LivePauseStore(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc=jdbc; this.json=json; }

    @Override @Transactional
    public void request(UUID runId, Ownership owner, Instant now, Instant deadline) {
        // Lock before INSERT, matching live dispatch admission and avoiding inverse row locks.
        jdbc.queryForList("select singleton_id from provider_campaign_guard where singleton_id=1 for update");
        jdbc.update("""
            insert into j3_live_pause(run_id,campaign_id,owner_instance_id,generation,phase,requested_at,deadline_at,changed_at)
            values(?,?,?,?,'REQUESTED',?,?,?)
            """,runId,owner.campaignId(),owner.instanceId(),owner.generation(),time(now),time(deadline),time(now));
    }

    @Override @Transactional
    public void transition(UUID runId, Ownership owner, String expected, String phase,
                           Instant now, String reason, List<MissedSlot> missed) {
        jdbc.queryForList("select singleton_id from provider_campaign_guard where singleton_id=1 for update");
        if(jdbc.update("""
            update j3_live_pause set phase=?,changed_at=?,reason=?,missed_slots=missed_slots || ?::jsonb
            where run_id=? and campaign_id=? and owner_instance_id=? and generation=? and phase=?
            """,phase,time(now),reason,json.writeValueAsString(missed),runId,owner.campaignId(),
                owner.instanceId(),owner.generation(),expected)!=1)
            throw new IllegalStateException("J3_LIVE_PAUSE_STATE_CHANGED");
    }

    @Override @Transactional(readOnly=true)
    public Optional<Pause> latest(UUID campaignId) {
        return jdbc.query("""
            select * from j3_live_pause where campaign_id=? order by requested_at desc,run_id desc limit 1
            """,(rs,n)->new Pause(rs.getObject("run_id",UUID.class),rs.getObject("campaign_id",UUID.class),
                rs.getLong("generation"),rs.getString("phase"),rs.getTimestamp("requested_at").toInstant(),
                rs.getTimestamp("deadline_at").toInstant(),rs.getTimestamp("changed_at").toInstant(),
                rs.getString("reason")),campaignId).stream().findFirst();
    }
    @Override @Transactional(readOnly=true)
    public Optional<Pause> forRun(UUID runId) {
        return jdbc.query("select * from j3_live_pause where run_id=?",
                (rs,n)->new Pause(rs.getObject("run_id",UUID.class),rs.getObject("campaign_id",UUID.class),
                    rs.getLong("generation"),rs.getString("phase"),rs.getTimestamp("requested_at").toInstant(),
                    rs.getTimestamp("deadline_at").toInstant(),rs.getTimestamp("changed_at").toInstant(),rs.getString("reason")),
                runId).stream().findFirst();
    }
    private static Timestamp time(Instant instant) { return Timestamp.from(instant); }
}
