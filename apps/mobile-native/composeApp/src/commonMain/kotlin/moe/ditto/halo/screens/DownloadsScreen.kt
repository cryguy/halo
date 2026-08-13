package moe.ditto.halo.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import moe.ditto.halo.ui.CenterMessage
import moe.ditto.halo.ui.HaloColors
import moe.ditto.halo.ui.HaloDimensions
import moe.ditto.halo.ui.HaloSpacing
import moe.ditto.halo.ui.rememberResponsive

/**
 * The production empty state for native downloads.
 *
 * The transfer engine is a separate platform feature. This screen stays
 * truthful until that engine can supply persisted entries and offline files.
 */
@Composable
internal fun DownloadsScreen(modifier: Modifier = Modifier) {
    val responsive = rememberResponsive()
    Box(modifier.fillMaxSize().background(HaloColors.Background)) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .then(
                    responsive.contentMaxWidth?.let { Modifier.widthIn(max = it).fillMaxWidth() }
                        ?: Modifier.fillMaxWidth(),
                )
                .align(Alignment.TopCenter)
                .padding(horizontal = HaloSpacing.Md),
        ) {
            ScreenHeader(title = "Downloads")
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                CenterMessage(
                    "Downloads will live here. The native offline transfer engine is not available yet.",
                )
            }
            Spacer(Modifier.height(HaloDimensions.TabBarSpace))
        }
    }
}
