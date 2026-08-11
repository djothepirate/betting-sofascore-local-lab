package com.bettingproject.sofascorelocal.fixture;

import java.util.Objects;
import java.util.Optional;

public record LoadedFixture(
        FixtureManifest manifest,
        byte[] rawPayload,
        FixtureContentKind contentKind,
        String rawSha256,
        Optional<String> canonicalJsonSha256) {

    public LoadedFixture {
        manifest = Objects.requireNonNull(manifest, "manifest");
        rawPayload = Objects.requireNonNull(rawPayload, "rawPayload").clone();
        contentKind = Objects.requireNonNull(contentKind, "contentKind");
        rawSha256 = Objects.requireNonNull(rawSha256, "rawSha256");
        canonicalJsonSha256 = Objects.requireNonNull(
                canonicalJsonSha256,
                "canonicalJsonSha256");
    }

    @Override
    public byte[] rawPayload() {
        return rawPayload.clone();
    }

    public int sizeBytes() {
        return rawPayload.length;
    }
}
