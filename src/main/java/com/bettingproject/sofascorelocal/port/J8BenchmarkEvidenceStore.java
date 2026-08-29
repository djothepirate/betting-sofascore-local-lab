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
}
