package com.cardpricer.util;

/**
 * Single source of truth for the current application version.
 * This constant is checked at startup against the latest GitHub release.
 */
public final class AppVersion {

    /** Current application version — update this with each release. */
    public static final String CURRENT = "2.0.8";

    /** GitHub owner/repo used by {@link com.cardpricer.service.UpdateCheckService}. */
    public static final String GITHUB_OWNER = "NinjaPanda351";
    public static final String GITHUB_REPO  = "OCC_PRICER";

    private AppVersion() {}
}
