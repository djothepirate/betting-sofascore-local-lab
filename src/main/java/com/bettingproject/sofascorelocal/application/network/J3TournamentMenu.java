package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.domain.provider.RawPayloadEvidence;
import com.bettingproject.sofascorelocal.domain.scheduledevents.J3TournamentCatalog;
import com.bettingproject.sofascorelocal.domain.scheduledevents.J3TournamentCatalogOption;
import com.bettingproject.sofascorelocal.port.RawSnapshotInspectionStore;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.core.JacksonException;
import java.text.Collator;
import java.util.*;
import java.util.regex.Pattern;

/** Display-only metadata from the preserved source pages; never changes selection identities. */
final class J3TournamentMenu {
    private static final Pattern AMATEUR = Pattern.compile("(?iu)(?<![\\p{L}\\p{N}])amateur(?![\\p{L}\\p{N}])");
    private record Metadata(int priority, boolean amateur, boolean qualification, String category) {}
    private static final Metadata DEFAULT = new Metadata(0, false, false, null);

    static List<J3TournamentCatalogOption> options(J3TournamentCatalog catalog,
            RawSnapshotInspectionStore snapshots, boolean includeAmateur) {
        return options(catalog, snapshots, includeAmateur, false);
    }

    static List<J3TournamentCatalogOption> options(J3TournamentCatalog catalog,
            RawSnapshotInspectionStore snapshots, boolean includeAmateur, boolean includeQualification) {
        if (!catalog.available()) return List.of();
        var metadata = new HashMap<Long, Metadata>();
        var mapper = JsonMapper.builder().build();
        for (long id : catalog.pageSnapshotIds()) {
            var source = snapshots.findById(id).orElseThrow(() -> new IllegalStateException("J3_MENU_SOURCE_ABSENT"));
            var payload = RawPayloadEvidence.capture(source.payloadRaw());
            if (payload.sizeBytes() != source.summary().payloadSizeBytes()
                    || !payload.sha256().equals(source.summary().payloadSha256()))
                throw new IllegalStateException("J3_MENU_SOURCE_INTEGRITY");
            JsonNode scheduled;
            try {
                scheduled = mapper.readTree(payload.bytes()).path("scheduled");
            } catch (JacksonException exception) {
                throw new IllegalStateException("J3_MENU_SOURCE_JSON");
            }
            if (!scheduled.isArray()) throw new IllegalStateException("J3_MENU_SOURCE_SHAPE");
            for (var item : scheduled) {
                var tournament = item.path("tournament");
                var category = tournament.path("category");
                var priority = category.path("priority");
                int value = priority.isIntegralNumber() && priority.canConvertToInt() && priority.asInt() > 0
                        ? priority.asInt() : 0;
                var next = new Metadata(value, amateur(category.path("name"))
                        || amateur(tournament.path("uniqueTournament").path("category").path("name")),
                        tournament.path("qualificationOrPreliminary").isBoolean()
                                && tournament.path("qualificationOrPreliminary").asBoolean(), translation(category));
                long tournamentId = tournament.path("id").asLong();
                metadata.merge(tournamentId, next, (a,b) -> new Metadata(
                        Math.min(a.priority(), b.priority()), a.amateur() || b.amateur(),
                        a.qualification() || b.qualification(), a.category()));
            }
        }
        var collator = Collator.getInstance(Locale.FRENCH);
        collator.setStrength(Collator.PRIMARY);
        Comparator<J3TournamentCatalogOption> order = Comparator
                .comparingInt((J3TournamentCatalogOption o) -> metadata.getOrDefault(o.tournamentId(), DEFAULT).priority() > 0 ? 0 : 1)
                .thenComparingInt(o -> metadata.getOrDefault(o.tournamentId(), DEFAULT).priority())
                .thenComparing(J3TournamentCatalogOption::tournamentCategoryName, collator)
                .thenComparing(J3TournamentCatalogOption::tournamentName, collator)
                .thenComparingLong(J3TournamentCatalogOption::tournamentId);
        return catalog.options().stream().filter(o -> includeAmateur
                || !(AMATEUR.matcher(o.tournamentCategoryName()).find()
                || metadata.getOrDefault(o.tournamentId(), DEFAULT).amateur()))
                .filter(o -> includeQualification || !metadata.getOrDefault(o.tournamentId(), DEFAULT).qualification())
                .map(o -> {
                    String translated = metadata.getOrDefault(o.tournamentId(), DEFAULT).category();
                    String label = translated == null ? J3CategoryLabels.french(o.tournamentCategoryName()) : translated;
                    return new J3TournamentCatalogOption(o.tournamentId(), o.tournamentName(),
                            label, o.uniqueTournamentId(), o.uniqueTournamentName(), o.timezoneEventCount(), o.sourceSnapshotIds());
                })
                .sorted(order).toList();
    }

    private static boolean amateur(JsonNode name) {
        return name.isString() && AMATEUR.matcher(name.asString()).find();
    }
    private static String translation(JsonNode category) {
        var fr = category.path("fieldTranslations").path("nameTranslation").path("fr");
        if (!fr.isString() || fr.asString().isBlank() || fr.asString().chars().anyMatch(Character::isISOControl)) return null;
        return fr.asString();
    }
}
