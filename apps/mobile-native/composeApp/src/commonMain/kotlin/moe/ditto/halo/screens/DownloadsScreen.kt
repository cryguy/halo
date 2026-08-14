package moe.ditto.halo.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import moe.ditto.halo.downloads.DownloadEntry
import moe.ditto.halo.downloads.DownloadStatus
import moe.ditto.halo.downloads.DownloadsCoordinator
import moe.ditto.halo.ui.CenterMessage
import moe.ditto.halo.ui.HaloAsyncImage
import moe.ditto.halo.ui.HaloColors
import moe.ditto.halo.ui.HaloDimensions
import moe.ditto.halo.ui.HaloIcons
import moe.ditto.halo.ui.HaloRadius
import moe.ditto.halo.ui.HaloSpacing
import moe.ditto.halo.ui.HaloType
import moe.ditto.halo.ui.monoStyle
import moe.ditto.halo.ui.SelectOption
import moe.ditto.halo.ui.SelectSheet
import moe.ditto.halo.ui.rememberResponsive

/**
 * What is on the device, grouped by the title it belongs to.
 *
 * Downloads are device-local, so this screen never waits on the network and
 * never shows a loading state: the index is read from the store when the
 * session's graph is built, which is why an entry is on screen the moment the
 * tab is opened, offline included.
 */
@Composable
internal fun DownloadsScreen(
    downloads: DownloadsCoordinator,
    onOpenDetail: (MetaRef) -> Unit,
    /** Plays a finished download from the device. Nothing here touches the network. */
    onPlay: (DownloadEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val responsive = rememberResponsive()
    val entries by downloads.entries.collectAsState()
    val scope = rememberCoroutineScope()
    var pendingRemoval by remember { mutableStateOf<DownloadEntry?>(null) }
    val groups = remember(entries) { groupDownloads(entries) }

    Box(modifier.fillMaxSize().background(HaloColors.Background)) {
        val content = Modifier
            .then(
                responsive.contentMaxWidth?.let { Modifier.widthIn(max = it).fillMaxWidth() }
                    ?: Modifier.fillMaxWidth(),
            )
            .align(Alignment.TopCenter)

        when {
            !downloads.isAvailable -> DownloadsPlaceholder(content) {
                CenterMessage("This device has nowhere to keep downloads.")
            }

            entries.isEmpty() -> DownloadsPlaceholder(content) {
                CenterMessage(
                    "Downloads live here. Pick a source on any title and tap the download icon, " +
                        "then it plays with no network at all.",
                )
            }

            else -> LazyColumn(
                modifier = content.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = HaloSpacing.Md,
                    end = HaloSpacing.Md,
                    // The tab bar floats over this screen, so the last row has
                    // to stop short of it or it sits behind the glass.
                    bottom = HaloDimensions.TabBarSpace,
                ),
            ) {
                item(key = "header") {
                    Column {
                        ScreenHeader(title = DownloadsTitle)
                        Text(
                            text = downloadsSummary(entries),
                            style = HaloType.Overline,
                            modifier = Modifier.padding(bottom = HaloSpacing.Sm),
                        )
                    }
                }
                // One item per title rather than one per row: the group is a
                // card, and a card cannot be assembled from separate items.
                // Groups are a season at most, so nothing large is composed
                // that is not on screen.
                items(items = groups, key = { it.itemId }) { group ->
                    Column(Modifier.padding(bottom = HaloSpacing.Md)) {
                        GroupHeader(
                            group = group,
                            onClick = { onOpenDetail(MetaRef(group.type, group.metaId)) },
                        )
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(HaloRadius.Lg))
                                .background(HaloColors.Glass)
                                .border(1.dp, HaloColors.GlassBorder, RoundedCornerShape(HaloRadius.Lg)),
                        ) {
                            group.entries.forEachIndexed { index, entry ->
                                if (index > 0) {
                                    Box(Modifier.fillMaxWidth().height(1.dp).background(HaloColors.Hairline))
                                }
                                DownloadRow(
                                    entry = entry,
                                    onPlay = { onPlay(entry) },
                                    onPause = { scope.launch { downloads.pause(it) } },
                                    onResume = { scope.launch { downloads.resume(it) } },
                                    onRemove = { pendingRemoval = entry },
                                )
                            }
                        }
                    }
                }
            }
        }

        // Last child of the root, per SelectSheet's own contract.
        val removal = pendingRemoval
        SelectSheet(
            visible = removal != null,
            title = "Delete this download?",
            description = removal?.let(::downloadRowTitle),
            options = listOf(
                SelectOption(
                    key = DeleteKey,
                    label = "Delete from device",
                    detail = "The video and its subtitle are removed. Nothing on the server changes.",
                    destructive = true,
                ),
            ),
            onSelect = {
                removal?.let { entry -> scope.launch { downloads.remove(entry.videoId) } }
                pendingRemoval = null
            },
            onClose = { pendingRemoval = null },
        )
    }
}

private const val DownloadsTitle = "Downloads"
private const val DeleteKey = "delete"

/** Header over whatever stands in for the list, carrying the list's own padding. */
@Composable
private fun DownloadsPlaceholder(modifier: Modifier, body: @Composable () -> Unit) {
    Column(modifier.fillMaxSize().padding(horizontal = HaloSpacing.Md)) {
        ScreenHeader(title = DownloadsTitle)
        Box(Modifier.weight(1f)) { body() }
    }
}

@Composable
private fun GroupHeader(group: DownloadGroup, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(top = HaloSpacing.Md, bottom = HaloSpacing.Sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HaloSpacing.Sm + 4.dp),
    ) {
        HaloAsyncImage(
            url = group.poster,
            contentDescription = null,
            modifier = Modifier
                .size(width = PosterWidth, height = PosterHeight)
                .clip(RoundedCornerShape(HaloRadius.Sm))
                .background(HaloColors.Surface),
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = group.name,
                style = HaloType.Heading,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = downloadGroupSummary(group.entries),
                style = HaloType.Caption,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun DownloadRow(
    entry: DownloadEntry,
    onPlay: () -> Unit,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onRemove: () -> Unit,
) {
    val playable = entry.status == DownloadStatus.Done
    Column(
        Modifier
            .fillMaxWidth()
            // The whole row plays, not just the button: a finished download is
            // a thing to watch, and the row is what a thumb lands on.
            .then(if (playable) Modifier.clickable(role = Role.Button, onClick = onPlay) else Modifier),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = HaloSpacing.Md, vertical = HaloSpacing.Sm + 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HaloSpacing.Sm),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = downloadRowTitle(entry),
                    color = HaloColors.Text,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = downloadStatusLabel(entry),
                    style = HaloType.Caption,
                    color = if (entry.status == DownloadStatus.Failed) HaloColors.Danger else HaloColors.TextDim,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
                entry.subtitle?.lang?.let { lang ->
                    Text(
                        text = "Subtitle: $lang",
                        style = HaloType.Caption,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                if (entry.status != DownloadStatus.Done) {
                    ProgressTrack(entry)
                }

            }
            if (playable) {
                RowAction(
                    icon = HaloIcons.Play,
                    description = "Play this download",
                    onClick = onPlay,
                )
            }
            when (entry.status) {
                DownloadStatus.Downloading, DownloadStatus.Queued -> RowAction(
                    icon = HaloIcons.Pause,
                    description = "Pause download",
                    onClick = { onPause(entry.videoId) },
                )
                DownloadStatus.Paused -> RowAction(
                    icon = HaloIcons.Play,
                    description = "Resume download",
                    onClick = { onResume(entry.videoId) },
                )
                DownloadStatus.Failed -> RowAction(
                    icon = HaloIcons.Refresh,
                    description = "Retry download",
                    tint = HaloColors.Danger,
                    onClick = { onResume(entry.videoId) },
                )
                DownloadStatus.Done -> Unit
            }
            RowAction(
                icon = HaloIcons.Trash,
                description = "Delete download",
                tint = HaloColors.TextDim,
                onClick = onRemove,
            )
        }
    }
}

/**
 * The bar under an unfinished row. A transfer whose size the source never
 * declared has no fraction to draw, so the bar stays out rather than inventing
 * one; the byte count above it is still moving.
 */
@Composable
private fun ProgressTrack(entry: DownloadEntry) {
    val fraction = entry.fraction ?: return
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = HaloSpacing.Sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HaloSpacing.Sm),
    ) {
        Box(
            Modifier
                .weight(1f)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.16f)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(4.dp)
                    .background(
                        if (entry.status == DownloadStatus.Downloading) HaloColors.Accent else HaloColors.TextDim,
                    ),
            )
        }
        // Tabular figures, so the number does not jitter sideways as it counts.
        Text(
            text = "${(fraction * 100).toInt()}%",
            style = monoStyle(fontSize = 11.sp, color = HaloColors.TextDim),
        )
    }
}

@Composable
private fun RowAction(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    tint: Color = HaloColors.Accent,
) {
    Icon(
        imageVector = icon,
        contentDescription = description,
        tint = tint,
        modifier = Modifier
            .clickable(role = Role.Button, onClick = onClick)
            .padding(HaloSpacing.Xs)
            .size(24.dp),
    )
}

private val PosterWidth = 46.dp
private val PosterHeight = 69.dp
