package eu.kanade.tachiyomi.ui.browse.novel

import androidx.activity.compose.BackHandler
import androidx.compose.animation.graphics.ExperimentalAnimationGraphicsApi
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import eu.kanade.novel.ui.library.NovelLibraryContent
import eu.kanade.novel.ui.library.NovelLibraryScreenModel
import eu.kanade.tachiyomi.ui.library.novel.NovelLibrarySettingsDialog
import eu.kanade.tachiyomi.ui.library.novel.NovelLibrarySettingsScreenModel
import eu.kanade.presentation.library.components.LibraryToolbar
import eu.kanade.presentation.library.components.LibraryToolbarTitle
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.main.MainActivity
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.EmptyScreenAction
import tachiyomi.presentation.core.screens.LoadingScreen

data object NovelBrowseTab : Tab {

    @OptIn(ExperimentalAnimationGraphicsApi::class)
    override val options: TabOptions
        @Composable get() {
            val isSelected = LocalTabNavigator.current.current.key == key
            val image = AnimatedImageVector.animatedVectorResource(R.drawable.anim_library_enter)
            return TabOptions(
                index = 4u,
                title = stringResource(MR.strings.novel_tab_title),
                icon = rememberAnimatedVectorPainter(image, isSelected),
            )
        }

    override suspend fun onReselect(navigator: Navigator) {
        requestOpenSettingsSheet()
    }

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current

        val screenModel = rememberScreenModel { NovelLibraryScreenModel() }
        val settingsScreenModel = rememberScreenModel { NovelLibrarySettingsScreenModel() }
        val novels by screenModel.novels.collectAsState()
        val searchQuery by screenModel.searchQuery.collectAsState()
        val showSettingsDialog by screenModel.showSettingsDialog.collectAsState()

        val snackbarHostState = remember { SnackbarHostState() }
        val haptic = LocalHapticFeedback.current

        val hiddenGestureModifier = Modifier.pointerInput(Unit) {
            awaitEachGesture {
                val first = awaitFirstDown(requireUnconsumed = false)
                val second = awaitPointerEvent().changes.firstOrNull { it.id != first.id } ?: return@awaitEachGesture
                val w = size.width.toFloat()
                val firstStart = first.position
                val secondStart = second.position
                val topThreshold = size.height * 0.2f
                val sideThreshold = w * 0.3f
                if (firstStart.y > topThreshold || secondStart.y > topThreshold) return@awaitEachGesture
                val leftFinger = if (firstStart.x < secondStart.x) first else second
                val rightFinger = if (firstStart.x < secondStart.x) second else first
                if (leftFinger.position.x > sideThreshold || rightFinger.position.x < w - sideThreshold) return@awaitEachGesture
                val threshold = 80f
                var leftMoved = false
                var rightMoved = false
                while (true) {
                    val event = awaitPointerEvent()
                    event.changes.forEach { change ->
                        if (change.id == leftFinger.id && change.position.x - leftFinger.position.x > threshold) leftMoved = true
                        if (change.id == rightFinger.id && rightFinger.position.x - change.position.x > threshold) rightMoved = true
                    }
                    if (leftMoved && rightMoved) {
                        screenModel.toggleHidden()
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        break
                    }
                    if (event.changes.all { !it.pressed }) break
                }
            }
        }

        Scaffold(
            modifier = hiddenGestureModifier,
            topBar = { scrollBehavior ->
                LibraryToolbar(
                    hasActiveFilters = false,
                    selectedCount = 0,
                    title = LibraryToolbarTitle(
                        text = stringResource(MR.strings.novel_tab_title),
                        numberOfEntries = novels.size.takeIf { it > 0 },
                    ),
                    onClickUnselectAll = {},
                    onClickSelectAll = {},
                    onClickInvertSelection = {},
                    onClickFilter = screenModel::openSettingsDialog,
                    onClickRefresh = {},
                    onClickGlobalUpdate = {},
                    onClickOpenRandomEntry = {},
                    searchQuery = searchQuery,
                    onSearchQueryChange = screenModel::search,
                    scrollBehavior = scrollBehavior,
                )
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        ) { contentPadding ->
            when {
                novels.isEmpty() && searchQuery.isNullOrEmpty() -> EmptyScreen(
                    stringRes = MR.strings.information_empty_library,
                    modifier = Modifier.padding(contentPadding),
                    actions = persistentListOf(
                        EmptyScreenAction(
                            stringRes = MR.strings.getting_started_guide,
                            icon = Icons.AutoMirrored.Outlined.HelpOutline,
                            onClick = {},
                        ),
                    ),
                )
                novels.isEmpty() -> EmptyScreen(
                    message = stringResource(MR.strings.no_results_found),
                    modifier = Modifier.padding(contentPadding),
                )
                else -> NovelLibraryContent(
                    novels = novels,
                    contentPadding = contentPadding,
                    onClickItem = { novel ->
                        navigator.push(eu.kanade.novel.ui.detail.NovelDetailScreen(novel.url, novel.source))
                    },
                )
            }
        }

        if (showSettingsDialog) {
            NovelLibrarySettingsDialog(
                onDismissRequest = screenModel::closeSettingsDialog,
                screenModel = settingsScreenModel,
            )
        }

        BackHandler(enabled = searchQuery != null) {
            screenModel.search(null)
        }

        LaunchedEffect(Unit) {
            queryEvent.receiveAsFlow().collectLatest { screenModel.search(it) }
            requestSettingsSheetEvent.receiveAsFlow().collectLatest { screenModel.openSettingsDialog() }
        }

        LaunchedEffect(Unit) {
            (context as? MainActivity)?.ready = true
        }
    }

    private val queryEvent = Channel<String>()
    private val requestSettingsSheetEvent = Channel<Unit>()

    suspend fun search(query: String) = queryEvent.send(query)
    private suspend fun requestOpenSettingsSheet() = requestSettingsSheetEvent.send(Unit)
}
