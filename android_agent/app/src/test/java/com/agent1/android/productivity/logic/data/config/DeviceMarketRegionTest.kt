package com.agent1.android.productivity.logic.data.config

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceMarketRegionTest {

    @Test
    fun cnSimOverridesLocale() {
        assertEquals(
            DeviceMarketRegion.Market.DOMESTIC_CN,
            DeviceMarketRegion.detectFromSignals("cn", "US"),
        )
    }

    @Test
    fun cnLocaleWithoutSim() {
        assertEquals(
            DeviceMarketRegion.Market.DOMESTIC_CN,
            DeviceMarketRegion.detectFromSignals(null, "CN"),
        )
    }

    @Test
    fun overseasSim() {
        assertEquals(
            DeviceMarketRegion.Market.OVERSEAS,
            DeviceMarketRegion.detectFromSignals("us", "CN"),
        )
    }

    @Test
    fun overseasLocale() {
        assertEquals(
            DeviceMarketRegion.Market.OVERSEAS,
            DeviceMarketRegion.detectFromSignals(null, "US"),
        )
    }
}
