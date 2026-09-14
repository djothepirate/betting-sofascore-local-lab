package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotInspectionSource;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotInspectionSummary;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotSchemaStatus;
import com.bettingproject.sofascorelocal.domain.scheduledevents.J3TournamentCatalog;
import com.bettingproject.sofascorelocal.domain.scheduledevents.J3TournamentCatalogOption;
import com.bettingproject.sofascorelocal.port.RawSnapshotInspectionStore;
import com.bettingproject.sofascorelocal.security.Sha256;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class J3TournamentMenuTest {
    private final RawSnapshotInspectionStore snapshots = mock(RawSnapshotInspectionStore.class);

    @Test
    void positivePrioritiesPrecedeZeroAndUseFrenchAlphabeticOrderWithinEachPriority() {
        var catalog = catalog(option(1, "Austria"), option(2, "Germany"), option(3, "Zimbabwe"),
                option(4, "France"), option(5, "England"));
        source("""
                {"scheduled":[
                  {"tournament":{"id":1,"category":{"priority":2}}},
                  {"tournament":{"id":2,"category":{"priority":2}}},
                  {"tournament":{"id":3,"category":{"priority":1}}},
                  {"tournament":{"id":4,"category":{"priority":0}}},
                  {"tournament":{"id":5,"category":{"priority":0}}}
                ]}
                """);
        var menu = J3TournamentMenu.options(catalog, snapshots, false);
        assertThat(menu).extracting(J3TournamentCatalogOption::tournamentId).containsExactly(3L,2L,1L,5L,4L);
        assertThat(menu).extracting(J3TournamentCatalogOption::tournamentCategoryName)
                .containsExactly("Zimbabwe", "Allemagne", "Autriche", "Angleterre", "France");
        assertThat(catalog.options().get(1).tournamentCategoryName()).isEqualTo("Germany");
        assertThat(menu.get(1).sourceSnapshotIds()).containsExactly(1L);
    }

    @Test
    void amateursInEitherSourceNameAreHiddenByDefaultEvenWhenTranslated() {
        var catalog = catalog(option(1,"Argentina Amateur"), option(2,"France"), option(3,"Germany"));
        source("""
                {"scheduled":[
                  {"tournament":{"id":1,"category":{"name":"Argentina Amateur",
                    "fieldTranslations":{"nameTranslation":{"fr":"Argentine"}}}}},
                  {"tournament":{"id":2,"category":{"name":"France"},
                    "uniqueTournament":{"category":{"name":"France aMaTeUr"}}}},
                  {"tournament":{"id":3,"category":{"name":"Germany"}}}
                ]}
                """);
        assertThat(J3TournamentMenu.options(catalog,snapshots,false))
                .extracting(J3TournamentCatalogOption::tournamentId).containsExactly(3L);
        assertThat(J3TournamentMenu.options(catalog,snapshots,true))
                .extracting(J3TournamentCatalogOption::tournamentId).containsExactly(3L,1L,2L);
    }

    @Test
    void missingOrInvalidPriorityIsNonPriorityAndUnknownLabelsArePreserved() {
        var catalog = catalog(option(1,"Zed League"),option(2,"Austria"),option(3,"Germany"),option(4,"France"));
        source("""
                {"scheduled":[
                  {"tournament":{"id":1,"category":{"priority":-1}}},
                  {"tournament":{"id":2,"category":{"priority":"1"}}},
                  {"tournament":{"id":3,"category":{"priority":999999999999}}},
                  {"tournament":{"id":4,"category":{}}}
                ]}
                """);
        assertThat(J3TournamentMenu.options(catalog,snapshots,false))
                .extracting(J3TournamentCatalogOption::tournamentCategoryName)
                .containsExactly("Allemagne","Autriche","France","Zed League");
    }

    @Test
    void providerFrenchTranslationTakesPrecedenceAndBlankTranslationFallsBackToLocalDictionary() {
        var catalog = catalog(option(1,"World"),option(2,"Germany"));
        source("""
                {"scheduled":[
                  {"tournament":{"id":1,"category":{"fieldTranslations":{"nameTranslation":{"fr":"International"}}}}},
                  {"tournament":{"id":2,"category":{"fieldTranslations":{"nameTranslation":{"fr":" "}}}}}
                ]}
                """);
        assertThat(J3TournamentMenu.options(catalog,snapshots,false))
                .extracting(J3TournamentCatalogOption::tournamentCategoryName)
                .containsExactly("Allemagne","International");
    }

    @Test
    void allAmateurSelectionCanBeEmptyAndRestored() {
        source("{\"scheduled\":[{\"tournament\":{\"id\":1}}]}");
        var catalog = catalog(option(1,"Argentina Amateur"));
        assertThat(J3TournamentMenu.options(catalog,snapshots,false)).isEmpty();
        assertThat(J3TournamentMenu.options(catalog,snapshots,true)).hasSize(1);
        assertThat(J3CategoryLabels.french("Argentina Amateur")).isEqualTo("Argentine Amateur");
    }

    @Test
    void missingAndCorruptSourcesFailClosedWithoutReturningUnfilteredOptions() {
        var catalog = catalog(option(1,"France"));
        assertThatThrownBy(()->J3TournamentMenu.options(catalog,snapshots,false))
                .hasMessage("J3_MENU_SOURCE_ABSENT");
        var valid=source("{\"scheduled\":[]}");
        when(snapshots.findById(1L)).thenReturn(Optional.of(new RawSnapshotInspectionSource(valid.summary(),
                "{\"scheduled\":[1]}".getBytes(StandardCharsets.UTF_8))));
        assertThatThrownBy(()->J3TournamentMenu.options(catalog,snapshots,false))
                .hasMessage("J3_MENU_SOURCE_INTEGRITY");
    }

    @Test
    void malformedJsonAndWrongShapeAreReportedWithoutPayload() {
        source("{");
        assertThatThrownBy(()->J3TournamentMenu.options(catalog(option(1,"France")),snapshots,false))
                .hasMessage("J3_MENU_SOURCE_JSON");
        source("{}");
        assertThatThrownBy(()->J3TournamentMenu.options(catalog(option(1,"France")),snapshots,false))
                .hasMessage("J3_MENU_SOURCE_SHAPE");
    }

    private static J3TournamentCatalogOption option(long id,String category) {
        return new J3TournamentCatalogOption(id,"League",category,id,"League",Map.of(7200,1),List.of(1L));
    }
    private static J3TournamentCatalog catalog(J3TournamentCatalogOption... options) {
        return J3TournamentCatalog.available(LocalDate.parse("2026-09-14"),List.of(1L),List.of(options),0);
    }
    private RawSnapshotInspectionSource source(String json) {
        byte[] raw=json.getBytes(StandardCharsets.UTF_8);
        var source=new RawSnapshotInspectionSource(new RawSnapshotInspectionSummary(1L,"SCHEDULED_EVENTS",
                "SCHEDULED_EVENTS|date=2026-09-14|page=1",Instant.parse("2026-09-14T08:00:00Z"),200,
                "application/json",raw.length,Sha256.hex(raw),"scheduled-events-v1",RawSnapshotSchemaStatus.PARSED),raw);
        when(snapshots.findById(1L)).thenReturn(Optional.of(source));
        return source;
    }
}
