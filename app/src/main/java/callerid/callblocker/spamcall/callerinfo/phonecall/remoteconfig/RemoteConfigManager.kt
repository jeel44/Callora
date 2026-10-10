package callerid.callblocker.spamcall.callerinfo.phonecall.remoteconfig

import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "RemoteConfigManager"

/**
 * Single owner of the app's [FirebaseRemoteConfig] instance — in-app defaults, the one startup
 * fetch, and typed accessors for every remote key, so no call site reads raw key strings itself.
 *
 * Every default here is the SAFE value: if the fetch fails (offline, first launch with no network,
 * Firebase outage), the app keeps behaving exactly as it would with no remote control at all —
 * ads in particular stay OFF, never ON by accident.
 */
object RemoteConfigManager {

    const val KEY_AD_APP_OPEN_ENABLED = "ad_app_open_enabled"
    const val KEY_AD_APP_OPEN_UNIT_ID = "ad_app_open_unit_id"
    const val KEY_AD_LANGUAGE_NATIVE_ENABLED = "ad_language_native_enabled"
    const val KEY_AD_LANGUAGE_NATIVE_UNIT_ID = "ad_language_native_unit_id"
    const val KEY_AD_ONBOARDING_NATIVE_ENABLED = "ad_onboarding_native_enabled"
    const val KEY_AD_ONBOARDING_NATIVE_UNIT_ID = "ad_onboarding_native_unit_id"
    const val KEY_AD_ONBOARDING_FULLSCREEN_ENABLED = "ad_onboarding_fullscreen_enabled"
    const val KEY_AD_ONBOARDING_FULLSCREEN_UNIT_ID = "ad_onboarding_fullscreen_unit_id"
    const val KEY_AD_ONBOARDING_INTERSTITIAL_ENABLED = "ad_onboarding_interstitial_enabled"
    const val KEY_AD_ONBOARDING_INTERSTITIAL_UNIT_ID = "ad_onboarding_interstitial_unit_id"
    const val KEY_AD_HOME_BANNER_ENABLED = "ad_home_banner_enabled"
    const val KEY_AD_HOME_BANNER_UNIT_ID = "ad_home_banner_unit_id"
    const val KEY_AD_CALLEND_NATIVE_ENABLED = "ad_callend_native_enabled"
    const val KEY_AD_CALLEND_NATIVE_UNIT_ID = "ad_callend_native_unit_id"

    // Google's official TEST App Open ad unit ID. TODO(before release): replace with Callora's
    // real App Open unit ID (here and/or in the Remote Config console).
    private const val DEFAULT_AD_APP_OPEN_UNIT_ID = "ca-app-pub-3940256099942544/9257395921"

    // Google's official TEST Native Advanced ad unit ID. TODO(before release): replace with
    // Callora's real Language Select native unit ID. (The post-call popup's native ad reuses this
    // test unit too and also needs its own real unit ID before release.)
    private const val DEFAULT_AD_LANGUAGE_NATIVE_UNIT_ID = "ca-app-pub-3940256099942544/2247696110"

    // Google's official TEST interstitial ad unit ID. TODO(before release): replace with Callora's
    // real onboarding interstitial unit ID. (Onboarding's inline and full-screen native ads reuse
    // the test Native Advanced unit above — full-screen native is the same format, just rendered
    // full-screen — and also need their own real unit IDs before release.)
    private const val DEFAULT_AD_ONBOARDING_INTERSTITIAL_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"

    // Google's official TEST adaptive banner ad unit ID. TODO(before release): replace with
    // Callora's real Home banner unit ID.
    private const val DEFAULT_AD_HOME_BANNER_UNIT_ID = "ca-app-pub-3940256099942544/9214589741"

    // TODO(before release): 0 means every cold start does a real network fetch — fine while
    // iterating on remote values in dev, but it will get throttled at scale. Raise to Firebase's
    // recommended 3600 (1 hour) or higher before shipping.
    private const val MINIMUM_FETCH_INTERVAL_SECONDS = 0L

    private val defaults: Map<String, Any> = mapOf(
        KEY_AD_APP_OPEN_ENABLED to false,
        KEY_AD_APP_OPEN_UNIT_ID to DEFAULT_AD_APP_OPEN_UNIT_ID,
        KEY_AD_LANGUAGE_NATIVE_ENABLED to false,
        KEY_AD_LANGUAGE_NATIVE_UNIT_ID to DEFAULT_AD_LANGUAGE_NATIVE_UNIT_ID,
        KEY_AD_ONBOARDING_NATIVE_ENABLED to false,
        KEY_AD_ONBOARDING_NATIVE_UNIT_ID to DEFAULT_AD_LANGUAGE_NATIVE_UNIT_ID,
        KEY_AD_ONBOARDING_FULLSCREEN_ENABLED to false,
        KEY_AD_ONBOARDING_FULLSCREEN_UNIT_ID to DEFAULT_AD_LANGUAGE_NATIVE_UNIT_ID,
        KEY_AD_ONBOARDING_INTERSTITIAL_ENABLED to false,
        KEY_AD_ONBOARDING_INTERSTITIAL_UNIT_ID to DEFAULT_AD_ONBOARDING_INTERSTITIAL_UNIT_ID,
        KEY_AD_HOME_BANNER_ENABLED to false,
        KEY_AD_HOME_BANNER_UNIT_ID to DEFAULT_AD_HOME_BANNER_UNIT_ID,
        KEY_AD_CALLEND_NATIVE_ENABLED to false,
        KEY_AD_CALLEND_NATIVE_UNIT_ID to DEFAULT_AD_LANGUAGE_NATIVE_UNIT_ID,
    )

    private val remoteConfig: FirebaseRemoteConfig by lazy { FirebaseRemoteConfig.getInstance() }

    // Completed once the startup fetch settles either way (success OR failure) — callers only
    // ever need "the best values this launch is going to get", not whether the fetch succeeded.
    private val initialFetch = CompletableDeferred<Unit>()

    private var defaultsApplied: Task<Void>? = null

    /** Called once from CalloraApplication.onCreate(). Non-blocking: settings, defaults and the
     * fetch are all async Tasks chained so the fetch only starts once defaults are in place. */
    fun initialize() {
        val settings = FirebaseRemoteConfigSettings.Builder()
            .setMinimumFetchIntervalInSeconds(MINIMUM_FETCH_INTERVAL_SECONDS)
            .build()
        val defaultsTask = remoteConfig.setConfigSettingsAsync(settings)
            .continueWithTask { remoteConfig.setDefaultsAsync(defaults) }
        defaultsApplied = defaultsTask
        defaultsTask
            .continueWithTask { remoteConfig.fetchAndActivate() }
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "fetchAndActivate succeeded, updated=${task.result}")
                } else {
                    Log.w(TAG, "fetchAndActivate failed — using cached/in-app defaults", task.exception)
                }
                initialFetch.complete(Unit)
            }
    }

    /** Suspends until the values activated by a PREVIOUS launch's fetch (or the in-app [defaults]
     * on a first-ever launch) are loaded from disk — fast and offline, unlike [awaitInitialFetch].
     * This is what lets a cold start start loading an ad without waiting on the network. */
    suspend fun awaitActivatedValues() {
        runCatching {
            defaultsApplied?.await()
            remoteConfig.ensureInitialized().await()
        }
            .onFailure { Log.w(TAG, "ensureInitialized failed — reads fall back to defaults", it) }
    }

    /** Suspends until the startup fetch settles, or [timeoutMillis] passes — whichever is first.
     * Values read afterwards are the freshest this launch has (fetched, else last-activated, else
     * the in-app [defaults]). */
    suspend fun awaitInitialFetch(timeoutMillis: Long) {
        withTimeoutOrNull(timeoutMillis) { initialFetch.await() }
    }

    val isAppOpenAdEnabled: Boolean
        get() = remoteConfig.getBoolean(KEY_AD_APP_OPEN_ENABLED)

    val appOpenAdUnitId: String
        get() = remoteConfig.getString(KEY_AD_APP_OPEN_UNIT_ID).ifBlank { DEFAULT_AD_APP_OPEN_UNIT_ID }

    val isLanguageNativeAdEnabled: Boolean
        get() = remoteConfig.getBoolean(KEY_AD_LANGUAGE_NATIVE_ENABLED)

    val languageNativeAdUnitId: String
        get() = remoteConfig.getString(KEY_AD_LANGUAGE_NATIVE_UNIT_ID).ifBlank { DEFAULT_AD_LANGUAGE_NATIVE_UNIT_ID }

    val isOnboardingNativeAdEnabled: Boolean
        get() = remoteConfig.getBoolean(KEY_AD_ONBOARDING_NATIVE_ENABLED)

    val onboardingNativeAdUnitId: String
        get() = remoteConfig.getString(KEY_AD_ONBOARDING_NATIVE_UNIT_ID).ifBlank { DEFAULT_AD_LANGUAGE_NATIVE_UNIT_ID }

    val isOnboardingFullscreenAdEnabled: Boolean
        get() = remoteConfig.getBoolean(KEY_AD_ONBOARDING_FULLSCREEN_ENABLED)

    val onboardingFullscreenAdUnitId: String
        get() = remoteConfig.getString(KEY_AD_ONBOARDING_FULLSCREEN_UNIT_ID).ifBlank { DEFAULT_AD_LANGUAGE_NATIVE_UNIT_ID }

    val isOnboardingInterstitialAdEnabled: Boolean
        get() = remoteConfig.getBoolean(KEY_AD_ONBOARDING_INTERSTITIAL_ENABLED)

    val onboardingInterstitialAdUnitId: String
        get() = remoteConfig.getString(KEY_AD_ONBOARDING_INTERSTITIAL_UNIT_ID).ifBlank { DEFAULT_AD_ONBOARDING_INTERSTITIAL_UNIT_ID }

    val isHomeBannerAdEnabled: Boolean
        get() = remoteConfig.getBoolean(KEY_AD_HOME_BANNER_ENABLED)

    val homeBannerAdUnitId: String
        get() = remoteConfig.getString(KEY_AD_HOME_BANNER_UNIT_ID).ifBlank { DEFAULT_AD_HOME_BANNER_UNIT_ID }

    val isCallEndNativeAdEnabled: Boolean
        get() = remoteConfig.getBoolean(KEY_AD_CALLEND_NATIVE_ENABLED)

    val callEndNativeAdUnitId: String
        get() = remoteConfig.getString(KEY_AD_CALLEND_NATIVE_UNIT_ID).ifBlank { DEFAULT_AD_LANGUAGE_NATIVE_UNIT_ID }
}
