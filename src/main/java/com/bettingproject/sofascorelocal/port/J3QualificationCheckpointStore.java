package com.bettingproject.sofascorelocal.port;

import com.bettingproject.sofascorelocal.domain.provider.J3StoredQualificationPage;

import java.time.LocalDate;
import java.util.List;

public interface J3QualificationCheckpointStore {

    List<J3StoredQualificationPage> findStoredPages(LocalDate qualificationDate);
}
