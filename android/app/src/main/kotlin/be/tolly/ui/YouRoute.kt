package be.tolly.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import be.tolly.core.designsystem.R
import be.tolly.core.designsystem.component.MessageState
import be.tolly.core.ui.PaneNavigation
import be.tolly.core.ui.TollyListDetailScaffold
import be.tolly.core.ui.rememberTwoPaneDirective
import be.tolly.feature.courses.grades.GradesOverviewPane
import be.tolly.feature.downloads.DownloadsRoute
import be.tolly.feature.settings.SettingsPane
import be.tolly.feature.settings.YouList
import be.tolly.feature.settings.YouSection
import kotlinx.coroutines.launch

/**
 * The You tab: the user's profile, and from there their grades across courses, their downloads
 * and Settings, which open on top on a phone and beside the list on a wide screen.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun YouRoute() {
    val navigator = rememberListDetailPaneScaffoldNavigator<YouSection>(scaffoldDirective = rememberTwoPaneDirective())
    val scope = rememberCoroutineScope()
    val listHidden = navigator.scaffoldValue[ListDetailPaneScaffoldRole.List] == PaneAdaptedValue.Hidden
    val navigation = if (listHidden) PaneNavigation.BACK else PaneNavigation.NONE
    val goBack: () -> Unit = { scope.launch { navigator.navigateBack() } }

    TollyListDetailScaffold(
        navigator = navigator,
        list = { shown ->
            YouList(
                selected = shown.takeIf { !listHidden },
                onOpen = { section -> scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, section) } },
            )
        },
        detail = { shown ->
            when (shown) {
                YouSection.GRADES -> GradesOverviewPane(navigation, goBack)
                YouSection.DOWNLOADS -> DownloadsRoute(navigation, goBack)
                YouSection.SETTINGS -> SettingsPane(navigation, goBack)
                null -> MessageState(icon = Icons.Outlined.Person, title = stringResource(R.string.you_select))
            }
        },
    )
}
