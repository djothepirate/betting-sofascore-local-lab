package com.bettingproject.sofascorelocal.port;

import com.bettingproject.sofascorelocal.application.benchmark.J8BenchmarkReadEvidence;
import com.bettingproject.sofascorelocal.application.benchmark.J8BenchmarkWindow;
import com.bettingproject.sofascorelocal.application.benchmark.J8DirectObservationCohorts;
import com.bettingproject.sofascorelocal.application.benchmark.J8LateChangeEvidence;

import java.time.Instant;
import java.util.List;

public interface J8BenchmarkReadStore {

    J8BenchmarkReadEvidence readEvidence(J8BenchmarkWindow window, Instant asOf);

    J8DirectObservationCohorts readDirectObservationCohorts(
            J8BenchmarkWindow window,
            Instant asOf);

    List<J8LateChangeEvidence> readLateChanges(
            J8BenchmarkWindow window,
            Instant asOf);
}
