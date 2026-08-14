package moe.ditto.halo

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import moe.ditto.halo.auth.AndroidSecureStorage
import moe.ditto.halo.auth.KtorAndroidOidcWire
import moe.ditto.halo.auth.KtorAuthConfigSource
import moe.ditto.halo.player.SubtitleFontLibrary
import moe.ditto.halo.storage.AndroidPreferencesStore
import java.io.File

/**
 * Android entry point. Assembles the exact same [PlatformDependencies] the iOS
 * `MainViewController` builds — only the concrete host implementations differ
 * (libmpv-on-SurfaceView here, MPVKit-on-UIView there). If the common shell
 * needed any Android-specific change to run, that would be a finding; it does
 * not.
 *
 * The host is created once and held across configuration changes (the manifest
 * declares configChanges so rotation does not recreate the activity), which is
 * how the mpv core survives rotation the way iOS's Swift-owned host does.
 */
class MainActivity : ComponentActivity() {

    private lateinit var playerHost: AndroidMpvPlayerHost
    private lateinit var authHost: AndroidOidcAuthHost
    private lateinit var authHttpClient: HttpClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        authHttpClient = HttpClient(OkHttp)
        val secureStorage = AndroidSecureStorage(applicationContext)
        authHost = AndroidOidcAuthHost(
            activity = this,
            storage = secureStorage,
            wire = KtorAndroidOidcWire(authHttpClient),
        )
        playerHost = AndroidMpvPlayerHost(applicationContext)

        // Automation may point at a fixture. With no override, using the shared
        // default preserves the last-successful-server prefill rule in HaloApp.
        val serverUrl = intent?.getStringExtra("serverUrl")
            ?: PlatformDependencies.DefaultServerUrl

        val dependencies = PlatformDependencies(
            authConfigSource = KtorAuthConfigSource(authHttpClient),
            nativeHostRequests = authHost,
            secureStorage = secureStorage,
            keyValueStore = AndroidPreferencesStore(applicationContext),
            // cacheDir, not filesDir: Android's auto-backup skips it and the
            // system may reclaim it, which suits re-fetchable poster art.
            imageCacheDirectory = File(applicationContext.cacheDir, "halo-images").path,
            // The manifest's own debuggable flag, so a release build cannot
            // reach the diagnostics harness. Read from ApplicationInfo rather
            // than BuildConfig, which this module does not generate.
            diagnosticsEnabled =
                (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0,
            playerPort = AndroidPlayerPort(playerHost),
            bundledSubtitleFonts = SubtitleFontLibrary.bundledFamilies(),
            playerEvents = playerHost.playerEvents,
            oidcSessionPort = authHost,
            authEvents = authHost.events,
            nativePlayerSurface = AndroidNativePlayerSurface(playerHost),
            nativeHostDiagnostics = AndroidNativeHostDiagnostics(authHost, playerHost),
            initialServerUrl = serverUrl,
            resetPersistedSession = intent?.getBooleanExtra("resetSession", false) ?: false,
        )

        setContent {
            HaloApp(dependencies)
        }

        authHost.handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        authHost.handleIntent(intent)
    }

    override fun onDestroy() {
        authHost.close()
        authHttpClient.close()
        super.onDestroy()
    }
}
