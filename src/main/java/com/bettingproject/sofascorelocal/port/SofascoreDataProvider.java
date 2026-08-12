package com.bettingproject.sofascorelocal.port;

import com.bettingproject.sofascorelocal.domain.provider.ProviderRequest;
import com.bettingproject.sofascorelocal.domain.provider.ProviderResponse;

public interface SofascoreDataProvider {

    ProviderResponse load(ProviderRequest request);
}
