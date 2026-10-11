package com.agent1.android.productivity.logic.data.config

import android.content.Context
import android.os.Build
import android.telephony.TelephonyManager
import java.util.Locale

/** 区分国内与海外默认模型栈（SIM 优先，其次系统 locale）。 */
object DeviceMarketRegion {

    enum class Market {
        DOMESTIC_CN,
        OVERSEAS,
    }

    fun detect(context: Context): Market {
        val sim = simCountryIso(context)
        val localeCountry = primaryLocale(context).country.uppercase(Locale.US)
        return detectFromSignals(sim, localeCountry)
    }

    internal fun detectFromSignals(simCountryIso: String?, localeCountryIso: String): Market {
        simCountryIso?.trim()?.uppercase(Locale.US)?.takeIf { it.isNotEmpty() }?.let { iso ->
            if (iso == "CN") return Market.DOMESTIC_CN
            return Market.OVERSEAS
        }
        if (localeCountryIso.uppercase(Locale.US) == "CN") return Market.DOMESTIC_CN
        return Market.OVERSEAS
    }

    private fun primaryLocale(context: Context): Locale {
        val config = context.resources.configuration
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (config.locales.isEmpty) Locale.getDefault() else config.locales[0]
        } else {
            @Suppress("DEPRECATION")
            config.locale ?: Locale.getDefault()
        }
    }

    private fun simCountryIso(context: Context): String? {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return null
        return tm.simCountryIso?.trim()?.uppercase(Locale.US)?.takeIf { it.isNotEmpty() }
    }
}
