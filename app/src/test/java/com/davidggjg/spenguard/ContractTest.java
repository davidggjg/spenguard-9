package com.davidggjg.spenguard;

import static org.junit.Assert.assertEquals;

import com.davidggjg.spenguard.receiver.SPenReceiver;
import com.davidggjg.spenguard.service.SPenGuardService;
import com.davidggjg.spenguard.service.WatchdogService;

import org.junit.Test;

/**
 * These constants are part of the app's persisted/inter-component contract.
 *
 * <p>The SharedPreferences name and key decide whether the guard re-arms
 * itself after a reboot, and the service actions are what the receivers and
 * notification PendingIntents send. Renaming any of them silently breaks
 * those flows at runtime with no compile error, so they are pinned here.
 */
public class ContractTest {

    @Test
    public void preferenceKeysAreStable() {
        assertEquals("spenguard_prefs", SPenReceiver.PREFS);
        assertEquals("guard_enabled", SPenReceiver.KEY_ENABLED);
    }

    @Test
    public void serviceActionsAreStable() {
        assertEquals("com.davidggjg.spenguard.SPEN_REMOVED",
                SPenGuardService.ACTION_SPEN_REMOVED);
        assertEquals("com.davidggjg.spenguard.STOP",
                SPenGuardService.ACTION_STOP);
        assertEquals("com.davidggjg.spenguard.WATCHDOG_START",
                WatchdogService.ACTION_START);
        assertEquals("com.davidggjg.spenguard.WATCHDOG_STOP",
                WatchdogService.ACTION_STOP);
    }

    @Test
    public void guardIsInactiveByDefault() {
        // Static state must start "off" so a stray STOP broadcast never
        // starts the alarm service just to stop it.
        org.junit.Assert.assertFalse(SPenGuardService.isActive());
    }
}
