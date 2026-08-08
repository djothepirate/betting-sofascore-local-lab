package com.geoffrey.betting.sofascorelocal.port;

import com.geoffrey.betting.sofascorelocal.domain.provider.ProviderRequest;
import com.geoffrey.betting.sofascorelocal.domain.provider.ProviderResponse;

public interface SofascoreDataProvider {

    ProviderResponse load(ProviderRequest request);
}
