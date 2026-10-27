package be.tolly.ui

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.LockClock
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import be.tolly.MainUiState
import be.tolly.MainViewModel
import be.tolly.core.auth.AuthState
import be.tolly.core.model.User
import be.tolly.core.designsystem.R
import be.tolly.core.designsystem.component.InfoBanner
import be.tolly.core.designsystem.component.TollyNavBar
import be.tolly.core.designsystem.component.TollyNavBarSpace
import be.tolly.core.designsystem.component.TollyTabBar
import be.tolly.core.designsystem.component.TollyTabBarSpace
import be.tolly.core.designsystem.component.TollyTabLabel
import be.tolly.core.designsystem.theme.LocalBottomBarInset
import be.tolly.core.designsystem.theme.LocalGlass
import be.tolly.core.designsystem.theme.Tolly
import be.tolly.core.designsystem.theme.glassBackdropSource
import be.tolly.core.ui.LocalOpenMailDraft
import be.tolly.core.ui.MailDraft
import be.tolly.core.ui.LocalRequestSignIn
import be.tolly.core.ui.PaneNavigation
import be.tolly.core.ui.ScreenTransitions
import be.tolly.core.ui.followBack
import be.tolly.feature.agenda.AgendaRoute
import be.tolly.feature.announcements.AnnouncementPane
import be.tolly.feature.announcements.FeedRoute
import be.tolly.feature.courses.CoursesRoute
import be.tolly.feature.login.LoginScreen
import be.tolly.feature.login.WelcomeScreen
import be.tolly.feature.settings.SetupScreen

private enum class TopLevel(
    val label: Int,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    COURSES(R.string.nav_courses, Icons.Outlined.School, Icons.Filled.School),
    AGENDA(R.string.nav_agenda, Icons.Outlined.CalendarMonth, Icons.Filled.CalendarMonth),
    ANNOUNCEMENTS(R.string.nav_announcements_short, Icons.Outlined.Campaign, Icons.Filled.Campaign),
    MAIL(R.string.nav_mail, Icons.Outlined.Mail, Icons.Filled.Mail),
    YOU(R.string.nav_you, Icons.Outlined.AccountCircle, Icons.Filled.AccountCircle),
}

/**
 * The screens of the main area, as keys of the back stack: a tab (`tab:AGENDA`), or a course
 * opened from the agenda, pushed over it (`course:_123_1`), whose back returns to the agenda.
 */
private fun tabKey(tab: TopLevel) = "tab:${tab.name}"

private fun courseKey(courseId: String) = "course:$courseId"

private val HOME = tabKey(TopLevel.COURSES)

/** The tab a screen belongs to: a course opened from the agenda counts as the Courses tab. */
private fun tabOf(key: String): TopLevel =
    if (key.startsWith("tab:")) TopLevel.valueOf(key.removePrefix("tab:")) else TopLevel.COURSES

/** How deep a screen is: the Courses tab is home, the other tabs one step away, a course over the agenda two. */
private fun depthOf(key: String): Int = when {
    key == HOME -> 0
    key.startsWith("tab:") -> 1
    else -> 2
}

@Composable
fun TollyApp(state: MainUiState, viewModel: MainViewModel) {
    Surface(Modifier.fillMaxSize(), color = Tolly.colors.screen) {
        when (val auth = state.auth) {
            AuthState.Loading -> Unit // the splash screen stays up
            AuthState.SignedOut -> SignedOutFlow(setupDone = state.preferences.setupDone, onDemo = viewModel::startDemo)
            is AuthState.SignedIn -> {
                val haptics = LocalHapticFeedback.current
                ShakeEffect(enabled = state.preferences.shakeToSwitchLook) {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    viewModel.toggleGlass()
                }
                MainScaffold(sessionValid = auth.sessionValid, user = auth.user, viewModel = viewModel)
            }
        }
    }
}

/** Welcome, then (on a new install) the quick setup, then signing in. */
@Composable
private fun SignedOutFlow(setupDone: Boolean, onDemo: () -> Unit) {
    var showSetup by rememberSaveable { mutableStateOf(false) }
    var showLogin by rememberSaveable { mutableStateOf(false) }
    when {
        showLogin -> LoginScreen(onSignedIn = { showLogin = false }, onClose = { showLogin = false })
        showSetup -> SetupScreen(
            onDone = {
                showSetup = false
                showLogin = true
            },
            onBack = { showSetup = false },
        )
        else -> WelcomeScreen(
            onSignIn = { if (setupDone) showLogin = true else showSetup = true },
            onDemo = onDemo,
            firstRun = !setupDone,
        )
    }
}

@Composable
private fun MainScaffold(sessionValid: Boolean, user: User, viewModel: MainViewModel) {
    // The Courses tab is home: another tab sits on it, so back returns there, and a course opened
    // from the agenda sits on the agenda.
    var stack by rememberSaveable(saver = listSaver(save = { it.value }, restore = { mutableStateOf(it) })) {
        mutableStateOf(listOf(HOME))
    }
    val top = stack.last()
    // Whether the latest change went back, which slides the screen off rather than fading tabs.
    var back by remember { mutableStateOf(false) }
    val unreadNews by viewModel.unreadAnnouncements.collectAsStateWithLifecycle()
    val unreadMail by viewModel.unreadMail.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // What each tab's badge counts: unread news, and unread mail (unless the user turned counts off).
    val badges = TopLevel.entries.map {
        when {
            !uiState.preferences.tabBadges -> 0
            it == TopLevel.ANNOUNCEMENTS -> unreadNews
            it == TopLevel.MAIL -> unreadMail ?: 0
            else -> 0
        }
    }
    // Outlook stays loaded while the other tabs are shown.
    val mail = remember { MailWebView() }
    DisposableEffect(mail) { onDispose { mail.destroy() } }
    LifecycleResumeEffect(Unit) {
        viewModel.refreshUnreadMail()
        onPauseOrDispose {}
    }
    var reLogin by rememberSaveable { mutableStateOf(false) }
    val requestSignIn: () -> Unit = remember { { reLogin = true } }
    val haptics = LocalHapticFeedback.current
    val openNews by viewModel.openNews.collectAsStateWithLifecycle()
    LaunchedEffect(openNews) {
        if (openNews == null) return@LaunchedEffect
        back = false
        stack = listOf(HOME, tabKey(TopLevel.ANNOUNCEMENTS))
    }
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    notice?.let { NoticeSheet(it, onDismiss = viewModel::dismissNotice) }
    val openMail by viewModel.openMail.collectAsStateWithLifecycle()
    LaunchedEffect(openMail) {
        if (!openMail) return@LaunchedEffect
        back = false
        stack = listOf(HOME, tabKey(TopLevel.MAIL))
        // The notification is about mail Outlook may not show yet.
        mail.refreshInbox()
        viewModel.openMailHandled()
    }
    val current = tabOf(top)
    val navigate: (TopLevel) -> Unit = { destination ->
        if (destination != current) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        back = false
        // A course opened from the agenda is left behind.
        stack = if (destination == TopLevel.COURSES) listOf(HOME) else listOf(HOME, tabKey(destination))
    }
    // Leaving the Mail tab, where mail may have been read: the badge asks again.
    var previous by remember { mutableStateOf(current) }
    LaunchedEffect(current) {
        if (previous == TopLevel.MAIL && current != TopLevel.MAIL) viewModel.refreshUnreadMail(force = true)
        previous = current
    }
    val pop: () -> Unit = {
        back = true
        stack = stack.dropLast(1)
    }

    val context = LocalContext.current
    val openMailDraft: (MailDraft) -> Unit = { draft ->
        mail.compose(context, user.email, draft)
        navigate(TopLevel.MAIL)
    }
    CompositionLocalProvider(LocalRequestSignIn provides requestSignIn, LocalOpenMailDraft provides openMailDraft) {
        val glass = LocalGlass.current
        val adaptiveType = NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(currentWindowAdaptiveInfo())
        // Phones get a floating bar that content scrolls under: Liquid Glass's tab bar, or a pill
        // like ObtainX's in Material. Wider windows keep the navigation rail.
        val floating = adaptiveType == NavigationSuiteType.NavigationBar
        val labels = TopLevel.entries.map { stringResource(it.label) }
        NavigationSuiteScaffold(
            layoutType = if (floating) NavigationSuiteType.None else adaptiveType,
            containerColor = Tolly.colors.screen,
            navigationSuiteColors = NavigationSuiteDefaults.colors(
                navigationBarContainerColor = if (glass) Tolly.colors.group else MaterialTheme.colorScheme.surfaceContainer,
                navigationRailContainerColor = Tolly.colors.screen,
                navigationDrawerContainerColor = Tolly.colors.screen,
            ),
            navigationSuiteItems = {
                TopLevel.entries.forEach { destination ->
                    item(
                        selected = destination == current,
                        onClick = { navigate(destination) },
                        icon = { NavIcon(destination, selected = destination == current, badge = badges[destination.ordinal]) },
                        label = { Text(stringResource(destination.label), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    )
                }
            },
        ) {
            Box(Modifier.fillMaxSize()) {
                val barSpace = when {
                    !floating -> 0.dp
                    glass -> TollyTabBarSpace
                    else -> TollyNavBarSpace
                }
                CompositionLocalProvider(LocalBottomBarInset provides barSpace) {
                    // Everything but the tab bar is what the tab bar's glass refracts; without a tab
                    // bar nothing needs recording. Nor does Mail: Outlook's WebView, drawn into the
                    // recording as well, runs out of tile memory and flickers, and it ends above the bar.
                    val record = floating && current != TopLevel.MAIL
                    Column(Modifier.fillMaxSize().then(if (record) Modifier.glassBackdropSource() else Modifier).background(Tolly.colors.screen)) {
                        if (!sessionValid) {
                            InfoBanner(
                                text = stringResource(R.string.session_expired_banner),
                                icon = Icons.Outlined.LockClock,
                                actionLabel = stringResource(R.string.action_sign_in),
                                onAction = requestSignIn,
                                modifier = Modifier
                                    .windowInsetsPadding(WindowInsets.statusBars)
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                            )
                        }
                        ScreenHost(
                            stack = stack,
                            back = back,
                            onBack = pop,
                            modifier = if (!sessionValid) Modifier.consumeWindowInsets(WindowInsets.statusBars) else Modifier,
                        ) { key ->
                            val announcementPane: @Composable (String, PaneNavigation, () -> Unit) -> Unit =
                                { id, navigation, onNavigate -> AnnouncementPane(id, navigation, onNavigate) }
                            when {
                                key.startsWith("course:") -> CoursesRoute(
                                    initialCourseId = key.removePrefix("course:"),
                                    onClose = pop,
                                    announcementPane = announcementPane,
                                )
                                else -> when (tabOf(key)) {
                                    TopLevel.COURSES -> CoursesRoute(announcementPane = announcementPane)
                                    TopLevel.AGENDA -> AgendaRoute(
                                        onOpenCourse = { courseId ->
                                            back = false
                                            stack = stack + courseKey(courseId)
                                        },
                                    )
                                    TopLevel.ANNOUNCEMENTS -> FeedRoute(
                                        openAnnouncementId = openNews?.announcementId,
                                        onOpenHandled = viewModel::openNewsHandled,
                                    )
                                    TopLevel.MAIL -> MailScreen(mail, unreadMail, email = user.email)
                                    TopLevel.YOU -> YouRoute()
                                }
                            }
                        }
                    }
                }
                if (floating && !glass) {
                    TollyNavBar(
                        itemCount = TopLevel.entries.size,
                        selectedIndex = current?.ordinal ?: -1,
                        onSelect = { navigate(TopLevel.entries[it]) },
                        label = { labels[it] },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    ) { index, selected -> NavIcon(TopLevel.entries[index], selected = selected, badge = badges[index]) }
                }
                if (floating && glass) {
                    TollyTabBar(
                        itemCount = TopLevel.entries.size,
                        selectedIndex = current?.ordinal ?: -1,
                        onSelect = { navigate(TopLevel.entries[it]) },
                        modifier = Modifier.align(Alignment.BottomCenter),
                        badge = { index -> if (badges[index] > 0) Badge { Text(badges[index].toString()) } },
                        badgeDescription = { index ->
                            val count = badges[index]
                            when {
                                count <= 0 -> null
                                TopLevel.entries[index] == TopLevel.MAIL -> pluralStringResource(R.plurals.mail_unread, count, count)
                                else -> pluralStringResource(R.plurals.unread_announcements, count, count)
                            }
                        },
                    ) { index, selected ->
                        val destination = TopLevel.entries[index]
                        Icon(if (selected) destination.selectedIcon else destination.icon, contentDescription = null)
                        TollyTabLabel(stringResource(destination.label))
                    }
                }
            }
        }
    }

    if (reLogin) {
        Dialog(
            onDismissRequest = { reLogin = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            Surface(Modifier.fillMaxSize()) {
                LoginScreen(onSignedIn = { reLogin = false }, onClose = { reLogin = false })
            }
        }
    }
}

/**
 * Shows the top of [stack] and moves between its screens like the panes inside a tab do: going
 * deeper slides the new screen in over the old one, going [back] slides it off again, and the back
 * gesture previews that all the way, under the finger. Switching tabs is a quick fade-through.
 * Each screen keeps its saved state (scroll positions, the open course) while it isn't shown.
 */
@Composable
private fun ScreenHost(
    stack: List<String>,
    back: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (key: String) -> Unit,
) {
    val top = stack.last()
    val screens = remember { SeekableTransitionState(top) }
    LaunchedEffect(top) { if (screens.currentState != top || screens.targetState != top) screens.animateTo(top) }
    // The latest [back], for the transition that starts with this change.
    val goingBack by rememberUpdatedState(back)
    var gestureBack by remember { mutableStateOf(false) }

    // Composed before the screens, so their own back handlers (a course's folders and panes) go first.
    PredictiveBackHandler(enabled = stack.size > 1) { events ->
        gestureBack = true
        try {
            screens.followBack(events, from = top, to = stack[stack.size - 2], onBack = onBack)
        } finally {
            gestureBack = false
        }
    }

    val saveable = rememberSaveableStateHolder()
    // A course screen that was closed forgets its state, so the next one starts at its content;
    // tabs keep theirs.
    var shown by remember { mutableStateOf(stack) }
    LaunchedEffect(stack) {
        shown.filter { it.startsWith("course:") && it !in stack }.forEach(saveable::removeState)
        shown = stack
    }
    rememberTransition(screens, label = "screens").AnimatedContent(
        modifier = modifier,
        transitionSpec = {
            val deeper = depthOf(targetState) > depthOf(initialState)
            when {
                goingBack || gestureBack -> ScreenTransitions.between(deeper = false)
                deeper && targetState.startsWith("course:") -> ScreenTransitions.between(deeper = true)
                // A quick fade-through between tabs; the default 700 ms cross-fade feels sluggish.
                else -> fadeIn(tween(TAB_FADE_IN_MS, delayMillis = TAB_FADE_OUT_MS)) togetherWith fadeOut(tween(TAB_FADE_OUT_MS))
            }
        },
    ) { key ->
        saveable.SaveableStateProvider(key) {
            // Opaque, so the screen it slides over (or off) isn't seen through it.
            Box(Modifier.fillMaxSize().background(Tolly.colors.screen)) { content(key) }
        }
    }
}

@Composable
private fun NavIcon(destination: TopLevel, selected: Boolean, badge: Int) {
    BadgedBox(badge = { if (badge > 0) Badge { Text(badge.toString()) } }) {
        Icon(if (selected) destination.selectedIcon else destination.icon, contentDescription = null)
    }
}

private const val TAB_FADE_OUT_MS = 60
private const val TAB_FADE_IN_MS = 120
