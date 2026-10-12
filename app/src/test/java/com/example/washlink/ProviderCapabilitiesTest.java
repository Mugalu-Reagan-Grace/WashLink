package com.example.washlink;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.washlink.data.ProviderCapabilities;

import org.junit.Test;

public class ProviderCapabilitiesTest {
    @Test
    public void explicitlyDisabledBookingModeIsNotSupported() {
        assertFalse(ProviderCapabilities.supportsBookingMode("Drop Off", false, true));
        assertFalse(ProviderCapabilities.supportsBookingMode("Pickup & Delivery", true, false));
    }

    @Test
    public void absentLegacyCapabilitiesRemainSupportedUntilProviderConfiguresThem() {
        assertTrue(ProviderCapabilities.supportsBookingMode("Drop Off", null, null));
        assertTrue(ProviderCapabilities.supportsBookingMode("Pickup & Delivery", null, null));
    }

    @Test
    public void modeMatchingIsCaseInsensitive() {
        assertFalse(ProviderCapabilities.supportsBookingMode("drop off", false, true));
        assertFalse(ProviderCapabilities.supportsBookingMode("PICKUP & DELIVERY", true, false));
    }

    @Test
    public void favoriteProviderPrefersPickupUnlessOnlyDropoffIsSupported() {
        assertTrue("Pickup & Delivery".equals(
                ProviderCapabilities.preferredBookingMode(true, true)));
        assertTrue("Drop Off".equals(
                ProviderCapabilities.preferredBookingMode(true, false)));
    }
}
