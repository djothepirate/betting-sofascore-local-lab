package com.bettingproject.sofascorelocal.adapter.bettingproject.transport;

import com.bettingproject.sofascorelocal.port.J7DeliveryTransportException;
import com.bettingproject.sofascorelocal.port.J7DeliveryTransportFailure;

/**
 * Captures the JDK HTTP retry properties at the first application instruction and refuses the
 * optional sender unless they were already exact. The properties must be JVM startup arguments;
 * changing them after application startup cannot satisfy this policy.
 */
public final class JdkHttpClientNoAutomaticRetryPolicy {

    public static final String DISABLE_RETRY_CONNECT =
            "jdk.httpclient.disableRetryConnect";
    public static final String REDIRECTS_RETRY_LIMIT =
            "jdk.httpclient.redirects.retrylimit";
    public static final String ENABLE_ALL_METHOD_RETRY =
            "jdk.httpclient.enableAllMethodRetry";

    private static final Observation STARTUP_OBSERVATION = observe();

    private JdkHttpClientNoAutomaticRetryPolicy() {
    }

    /**
     * Deliberately invoked as the first instruction of the application main method. Class
     * initialization captures the three properties before Spring or application HTTP clients run.
     */
    public static void recordApplicationStartup() {
        // Class initialization is the immutable record. Normal local startup remains permitted
        // when the policy is absent; only construction/use of the optional sender will be refused.
    }

    public static void requireSatisfiedFromStartupAndNow() {
        Observation current = observe();
        if (!STARTUP_OBSERVATION.satisfied() || !current.satisfied()) {
            throw new J7DeliveryTransportException(
                    J7DeliveryTransportFailure.AUTOMATIC_REPLAY_BLOCKED);
        }
    }

    static boolean startupObservationSatisfied() {
        return STARTUP_OBSERVATION.satisfied();
    }

    private static Observation observe() {
        try {
            return new Observation(
                    "true".equals(System.getProperty(DISABLE_RETRY_CONNECT)),
                    "1".equals(System.getProperty(REDIRECTS_RETRY_LIMIT)),
                    "false".equals(System.getProperty(ENABLE_ALL_METHOD_RETRY)));
        }
        catch (SecurityException exception) {
            return new Observation(false, false, false);
        }
    }

    private record Observation(
            boolean retryConnectDisabled,
            boolean retryLimitOne,
            boolean allMethodRetryDisabled) {

        private boolean satisfied() {
            return retryConnectDisabled && retryLimitOne && allMethodRetryDisabled;
        }
    }
}
