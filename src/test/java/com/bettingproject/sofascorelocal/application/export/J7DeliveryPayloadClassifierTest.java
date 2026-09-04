package com.bettingproject.sofascorelocal.application.export;

import com.bettingproject.sofascorelocal.application.delivery.J7DeliveryPayloadClass;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import static org.assertj.core.api.Assertions.assertThat;

class J7DeliveryPayloadClassifierTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Test
    void classifiesOnlyConcreteSyntheticEvidenceAsSynthetic() {
        ArrayNode sources = sources(
                source("PRESENT", "SYNTHETIC_FIXTURE"),
                source("MISSING", null),
                source("EMPTY_VALID", "SYNTHETIC_FIXTURE"),
                source("UNAVAILABLE", "SYNTHETIC_FIXTURE"),
                source("MISSING", null));

        assertThat(J7DeliveryPayloadClassifier.classify(sources))
                .isEqualTo(J7DeliveryPayloadClass.SYNTHETIC_ONLY);
    }

    @Test
    void classifiesOnlyConcreteProviderEvidenceAsProviderDerived() {
        ArrayNode sources = sources(
                source("PRESENT", "PROVIDER_SNAPSHOT"),
                source("PRESENT", "PROVIDER_SNAPSHOT"),
                source("UNAVAILABLE", "PROVIDER_SNAPSHOT"),
                source("MISSING", null),
                source("MISSING", null));

        assertThat(J7DeliveryPayloadClassifier.classify(sources))
                .isEqualTo(J7DeliveryPayloadClass.PROVIDER_DERIVED);
    }

    @Test
    void classifiesMixedConcreteEvidenceAsUnknown() {
        ArrayNode sources = sources(
                source("PRESENT", "PROVIDER_SNAPSHOT"),
                source("PRESENT", "SYNTHETIC_FIXTURE"),
                source("MISSING", null),
                source("MISSING", null),
                source("MISSING", null));

        assertThat(J7DeliveryPayloadClassifier.classify(sources))
                .isEqualTo(J7DeliveryPayloadClass.MIXED_OR_UNKNOWN);
    }

    @Test
    void failsClosedForIncompleteUnknownOrIncoherentEvidence() {
        ArrayNode tooShort = sources(
                source("PRESENT", "SYNTHETIC_FIXTURE"));
        ArrayNode unknownKind = sources(
                source("PRESENT", "SYNTHETIC_FIXTURE"),
                source("PRESENT", "UNRECOGNIZED"),
                source("MISSING", null),
                source("MISSING", null),
                source("MISSING", null));
        ArrayNode missingWithKind = sources(
                source("PRESENT", "SYNTHETIC_FIXTURE"),
                source("MISSING", "SYNTHETIC_FIXTURE"),
                source("MISSING", null),
                source("MISSING", null),
                source("MISSING", null));
        ArrayNode noConcreteEvidence = sources(
                source("MISSING", null),
                source("MISSING", null),
                source("MISSING", null),
                source("MISSING", null),
                source("MISSING", null));

        assertThat(J7DeliveryPayloadClassifier.classify(null))
                .isEqualTo(J7DeliveryPayloadClass.MIXED_OR_UNKNOWN);
        assertThat(J7DeliveryPayloadClassifier.classify(tooShort))
                .isEqualTo(J7DeliveryPayloadClass.MIXED_OR_UNKNOWN);
        assertThat(J7DeliveryPayloadClassifier.classify(unknownKind))
                .isEqualTo(J7DeliveryPayloadClass.MIXED_OR_UNKNOWN);
        assertThat(J7DeliveryPayloadClassifier.classify(missingWithKind))
                .isEqualTo(J7DeliveryPayloadClass.MIXED_OR_UNKNOWN);
        assertThat(J7DeliveryPayloadClassifier.classify(noConcreteEvidence))
                .isEqualTo(J7DeliveryPayloadClass.MIXED_OR_UNKNOWN);
    }

    private static ArrayNode sources(ObjectNode... values) {
        ArrayNode sources = MAPPER.createArrayNode();
        for (ObjectNode value : values) {
            sources.add(value);
        }
        return sources;
    }

    private static ObjectNode source(String availability, String sourceKind) {
        ObjectNode source = MAPPER.createObjectNode();
        source.put("availability", availability);
        if (sourceKind == null) {
            source.putNull("sourceKind");
        }
        else {
            source.put("sourceKind", sourceKind);
        }
        return source;
    }
}
