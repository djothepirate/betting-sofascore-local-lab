package com.bettingproject.sofascorelocal.application.live;

/** Free bytes on the PostgreSQL data filesystem, not the application's unrelated filesystem. */
public interface LiveStorageCapacityProbe { long availableBytes(); }
