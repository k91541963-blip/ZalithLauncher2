/*
 * AetherLite Launcher
 * Fork of Zalith Launcher 2, original Copyright (C) 2025-2026 MovTery and contributors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package com.movtery.zalithlauncher.ui.screens.content.home

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.game.account.AccountsManager
import com.movtery.zalithlauncher.game.home.HeroSession
import com.movtery.zalithlauncher.game.home.HeroVersion
import com.movtery.zalithlauncher.game.home.HomeHeroBuilder
import com.movtery.zalithlauncher.game.home.HomeHeroState
import com.movtery.zalithlauncher.game.home.HomePlayBlock
import com.movtery.zalithlauncher.game.home.HomeWorlds
import com.movtery.zalithlauncher.game.home.RecentWorld
import com.movtery.zalithlauncher.game.performance.GameSessionStore
import com.movtery.zalithlauncher.game.version.installed.Version
import com.movtery.zalithlauncher.game.version.installed.VersionsManager
import com.movtery.zalithlauncher.ui.components.BackgroundCard
import com.movtery.zalithlauncher.ui.components.ScalingActionButton
import com.movtery.zalithlauncher.ui.screens.content.elements.PlayerFace
import com.movtery.zalithlauncher.ui.screens.content.home.version.LocalHomeCardLauncher
import com.movtery.zalithlauncher.ui.screens.content.home.version.LocalHomeCardVersionSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/** The card switches to a side-by-side layout at this width. */
private val HeroExpandedWidth = 520.dp

/**
 * AetherUI 3.0 — home hero card.
 *
 * Everything on this card comes from real launcher state: the selected account, the
 * selected version (with its loader string), the newest world folder of that version and
 * the last measured session. Missing data is hidden, never replaced by a placeholder
 * number. The Play button is only enabled when both an account and a version exist, and
 * the reason is shown when it is disabled.
 */
@Composable
fun HomeHeroCard(
    isVisible: Boolean,
    onOpenAccountManager: () -> Unit,
    onOpenVersionManager: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val account by AccountsManager.currentAccountFlow.collectAsStateWithLifecycle()
    val version by VersionsManager.currentVersion.collectAsStateWithLifecycle()
    val onLaunch = LocalHomeCardLauncher.current
    val onOpenVersionSettings = LocalHomeCardVersionSettings.current

    var lastWorld by remember(version) { mutableStateOf<RecentWorld?>(null) }
    var lastSession by remember { mutableStateOf<HeroSession?>(null) }

    // Reads happen only while the home screen is visible; leaving cancels them.
    LaunchedEffect(version, isVisible) {
        if (!isVisible) return@LaunchedEffect
        lastWorld = withContext(Dispatchers.IO) {
            version?.let { HomeWorlds.findLatest(File(it.getGameDir(), "saves")) }
        }
    }
    LaunchedEffect(isVisible) {
        if (!isVisible) return@LaunchedEffect
        lastSession = withContext(Dispatchers.IO) {
            GameSessionStore.readLast(context)?.let(HomeHeroBuilder::fromSummary)
        }
    }

    val heroVersion = remember(version) {
        version?.let { selected ->
            HeroVersion(
                name = selected.getVersionName(),
                summary = if (selected.isValid()) selected.getVersionSummary() else null
            )
        }
    }
    val state = HomeHeroState(
        accountName = account?.username,
        version = heroVersion,
        lastWorld = lastWorld,
        lastSession = lastSession
    )
    val onVersionClick: () -> Unit = {
        val selected = version
        if (selected == null) onOpenVersionManager() else onOpenVersionSettings(selected)
    }

    BackgroundCard(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        BoxWithConstraints(modifier = Modifier.padding(all = 14.dp)) {
            val expanded = maxWidth >= HeroExpandedWidth
            if (expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Identity(
                            modifier = Modifier.weight(1f),
                            accountName = state.accountName,
                            version = state.version,
                            onOpenAccountManager = onOpenAccountManager,
                            onVersionClick = onVersionClick
                        )
                        Spacer(Modifier.width(12.dp))
                        PlayArea(
                            state = state,
                            modifier = Modifier.width(180.dp),
                            version = version,
                            onLaunch = onLaunch,
                            onOpenAccountManager = onOpenAccountManager,
                            onOpenVersionManager = onOpenVersionManager
                        )
                    }
                    Meta(state = state)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Identity(
                        modifier = Modifier.fillMaxWidth(),
                        accountName = state.accountName,
                        version = state.version,
                        onOpenAccountManager = onOpenAccountManager,
                        onVersionClick = onVersionClick
                    )
                    Meta(state = state)
                    PlayArea(
                        state = state,
                        modifier = Modifier.fillMaxWidth(),
                        version = version,
                        onLaunch = onLaunch,
                        onOpenAccountManager = onOpenAccountManager,
                        onOpenVersionManager = onOpenVersionManager
                    )
                }
            }
        }
    }
}

@Composable
private fun Identity(
    accountName: String?,
    version: HeroVersion?,
    onOpenAccountManager: () -> Unit,
    onVersionClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val account by AccountsManager.currentAccountFlow.collectAsStateWithLifecycle()
    val currentAccount = account

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (currentAccount != null) {
            PlayerFace(
                account = currentAccount,
                avatarSize = 52.dp
            )
        } else {
            Icon(
                modifier = Modifier.size(44.dp),
                painter = painterResource(R.drawable.ic_add),
                contentDescription = null
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                modifier = Modifier
                    .clickable(onClick = onOpenAccountManager)
                    .fillMaxWidth(),
                text = accountName ?: stringResource(R.string.home_hero_add_account),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                modifier = Modifier
                    .clickable(onClick = onVersionClick)
                    .fillMaxWidth(),
                text = version?.name ?: stringResource(R.string.home_hero_pick_version),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            version?.summary?.let { summary ->
                Text(
                    modifier = Modifier.alpha(0.65f),
                    text = summary,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun Meta(state: HomeHeroState) {
    val world = state.lastWorld
    val session = state.lastSession
    if (world == null && session == null) return

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        world?.let {
            Text(
                modifier = Modifier.alpha(0.75f),
                text = stringResource(R.string.home_hero_last_world, it.name),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (it.lastPlayedAt > 0) {
                Text(
                    modifier = Modifier.alpha(0.55f),
                    text = DateUtils.getRelativeTimeSpanString(
                        it.lastPlayedAt,
                        System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS
                    ).toString(),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
        session?.let {
            val minutes = (it.durationSeconds / 60).coerceAtLeast(1)
            Text(
                modifier = Modifier.alpha(0.75f),
                text = if (it.averageFps != null) {
                    stringResource(
                        R.string.home_hero_last_session_fps,
                        it.versionName,
                        String.format(Locale.getDefault(), "%.1f", it.averageFps),
                        minutes
                    )
                } else {
                    stringResource(
                        R.string.home_hero_last_session,
                        it.versionName,
                        minutes
                    )
                },
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PlayArea(
    state: HomeHeroState,
    version: Version?,
    onLaunch: (Version) -> Unit,
    onOpenAccountManager: () -> Unit,
    onOpenVersionManager: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        ScalingActionButton(
            modifier = Modifier.fillMaxWidth(),
            enabled = state.canPlay,
            onClick = {
                val target = version
                if (target == null) onOpenVersionManager() else onLaunch(target)
            }
        ) {
            Icon(
                modifier = Modifier.size(18.dp),
                painter = painterResource(R.drawable.ic_play_arrow_filled),
                contentDescription = null
            )
            Spacer(Modifier.width(6.dp))
            Text(text = stringResource(R.string.home_hero_play))
        }
        if (!state.canPlay) {
            Text(
                modifier = Modifier
                    .alpha(0.7f)
                    .clickable(
                        onClick = {
                            when (state.playBlock) {
                                HomePlayBlock.NO_ACCOUNT,
                                HomePlayBlock.NO_ACCOUNT_AND_VERSION -> onOpenAccountManager()
                                HomePlayBlock.NO_VERSION -> onOpenVersionManager()
                                null -> Unit
                            }
                        }
                    ),
                text = stringResource(
                    when (state.playBlock) {
                        HomePlayBlock.NO_ACCOUNT -> R.string.home_hero_need_account
                        HomePlayBlock.NO_VERSION -> R.string.home_hero_need_version
                        HomePlayBlock.NO_ACCOUNT_AND_VERSION,
                        null -> R.string.home_hero_need_account_version
                    }
                ),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2
            )
        }
    }
}
