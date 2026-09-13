package com.bettingproject.sofascorelocal.port;

import com.bettingproject.sofascorelocal.domain.benchmark.J8BenchmarkCampaign;
import com.bettingproject.sofascorelocal.domain.benchmark.J8BenchmarkCampaignResult;
import com.bettingproject.sofascorelocal.domain.benchmark.J8BenchmarkUnit;
import com.bettingproject.sofascorelocal.domain.benchmark.J8BenchmarkUnitResult;
import com.bettingproject.sofascorelocal.domain.benchmark.J8ProviderCallAttempt;

public interface J8BenchmarkEvidenceStore {

    void startCampaign(J8BenchmarkCampaign campaign);

    long declareUnit(J8BenchmarkUnit unit);

    long startProviderAttempt(J8ProviderCallAttempt attempt);

    void recordUnitResult(J8BenchmarkUnitResult result);

    void finishCampaign(J8BenchmarkCampaignResult result);

    /** WO-060 only: join the J3 catalogue publication transaction; never silently use REQUIRES_NEW. */
    default void finishCampaignWithCollection(J8BenchmarkCampaignResult result) {
        throw new UnsupportedOperationException("J3 atomic audit publication is unavailable");
    }
}
