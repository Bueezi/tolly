package be.tolly.feature.courses.customize

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import be.tolly.core.data.CourseCustomizationRepository
import be.tolly.core.designsystem.R
import be.tolly.core.designsystem.component.CourseAvatar
import be.tolly.core.designsystem.component.TollySheet
import be.tolly.core.designsystem.component.courseColors
import be.tolly.core.designsystem.component.courseIconVector
import be.tolly.core.model.Course
import be.tolly.core.model.CourseColor
import be.tolly.core.model.CourseCustomization
import be.tolly.core.model.CourseIcon
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CustomizeCourseViewModel @Inject constructor(
    private val repository: CourseCustomizationRepository,
) : ViewModel() {
    fun customization(courseId: String): Flow<CourseCustomization> =
        repository.customizations.map { it.byCourse[courseId] ?: CourseCustomization() }

    fun setNickname(course: Course, nickname: String) {
        val value = nickname.trim().takeIf { it.isNotEmpty() && it != course.name }
        viewModelScope.launch { repository.setNickname(course.id, value) }
    }

    fun setIcon(courseId: String, icon: CourseIcon?) = viewModelScope.launch { repository.setIcon(courseId, icon) }

    fun setColor(courseId: String, color: CourseColor?) = viewModelScope.launch { repository.setColor(courseId, color) }

    fun reset(courseId: String) = viewModelScope.launch { repository.reset(courseId) }
}

/**
 * Bottom sheet to rename a course and pick its icon and colour. Icon and colour apply right away
 * (the list behind the sheet updates live); the name is saved when the sheet closes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomizeCourseSheet(
    course: Course,
    onDismiss: () -> Unit,
    viewModel: CustomizeCourseViewModel = hiltViewModel(),
) {
    val custom by remember(course.id) { viewModel.customization(course.id) }.collectAsStateWithLifecycle(CourseCustomization())
    var name by rememberSaveable(course.id) { mutableStateOf(course.displayName) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    fun close() {
        viewModel.setNickname(course, name)
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    TollySheet(
        onDismissRequest = {
            viewModel.setNickname(course, name)
            onDismiss()
        },
        sheetState = sheetState,
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                CourseAvatar(name = name.ifBlank { course.name }, seed = course.id, size = 56.dp, icon = course.icon, color = course.color)
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.customize_title), style = MaterialTheme.typography.titleLarge)
                    Text(
                        listOfNotNull(course.name, course.code).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.customize_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { viewModel.setNickname(course, name) }),
                trailingIcon = if (name != course.name) {
                    {
                        IconButton(onClick = { name = course.name }) {
                            Icon(Icons.Outlined.Restore, contentDescription = stringResource(R.string.customize_original_name))
                        }
                    }
                } else {
                    null
                },
                modifier = Modifier.fillMaxWidth(),
            )

            SectionLabel(R.string.customize_icon)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Choice(
                    selected = custom.icon == null,
                    label = stringResource(R.string.customize_automatic),
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        viewModel.setIcon(course.id, null)
                    },
                    container = MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    Icon(Icons.Outlined.AutoAwesome, contentDescription = null)
                }
                CourseIcon.entries.forEach { icon ->
                    val (container, content) = courseColors(course.id, course.color)
                    Choice(
                        selected = custom.icon == icon,
                        label = stringResource(iconLabel(icon)),
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            viewModel.setIcon(course.id, icon)
                        },
                        container = container,
                    ) {
                        val vector = courseIconVector(icon)
                        if (vector != null) {
                            Icon(vector, contentDescription = null, tint = content)
                        } else {
                            Text("Aa", color = content, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            SectionLabel(R.string.customize_color)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ColorChoice(
                    selected = custom.color == null,
                    label = stringResource(R.string.customize_automatic),
                    color = course.icon.defaultColor, // what "automatic" currently resolves to
                    automatic = true,
                    seed = course.id,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        viewModel.setColor(course.id, null)
                    },
                )
                CourseColor.entries.forEach { color ->
                    ColorChoice(
                        selected = custom.color == color,
                        label = stringResource(colorLabel(color)),
                        color = color,
                        seed = course.id,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            viewModel.setColor(course.id, color)
                        },
                    )
                }
            }

            Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                val customised = custom != CourseCustomization() || name != course.name
                TextButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        name = course.name
                        viewModel.reset(course.id)
                    },
                    enabled = customised,
                ) { Text(stringResource(R.string.customize_reset)) }
                Spacer(Modifier.weight(1f))
                Button(onClick = ::close) { Text(stringResource(R.string.action_done)) }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: Int) {
    Text(stringResource(text), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}

/** A 48 dp tile in the icon grid; the selected one gets a thick primary outline. */
@Composable
private fun Choice(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    container: Color,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = container,
        border = if (selected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier
            .size(48.dp)
            .semantics {
                contentDescription = label
                this.selected = selected
                role = Role.RadioButton
            },
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
private fun ColorChoice(
    selected: Boolean,
    label: String,
    color: CourseColor?,
    seed: String,
    onClick: () -> Unit,
    automatic: Boolean = false,
) {
    val (container, content) = courseColors(seed, color)
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = container,
        border = BorderStroke(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .size(40.dp)
            .semantics {
                contentDescription = label
                this.selected = selected
                role = Role.RadioButton
            },
    ) {
        Box(contentAlignment = Alignment.Center) {
            when {
                selected -> Icon(Icons.Filled.Check, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
                automatic -> Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
            }
        }
    }
}

private fun iconLabel(icon: CourseIcon): Int = when (icon) {
    CourseIcon.INITIALS -> R.string.course_icon_initials
    CourseIcon.MATH -> R.string.course_icon_math
    CourseIcon.STATISTICS -> R.string.course_icon_statistics
    CourseIcon.CODE -> R.string.course_icon_code
    CourseIcon.TOOLS -> R.string.course_icon_tools
    CourseIcon.DATABASE -> R.string.course_icon_database
    CourseIcon.LOGIC -> R.string.course_icon_logic
    CourseIcon.AI -> R.string.course_icon_ai
    CourseIcon.GRAPHICS -> R.string.course_icon_graphics
    CourseIcon.HCI -> R.string.course_icon_hci
    CourseIcon.WEB -> R.string.course_icon_web
    CourseIcon.NETWORK -> R.string.course_icon_network
    CourseIcon.CLOUD -> R.string.course_icon_cloud
    CourseIcon.SECURITY -> R.string.course_icon_security
    CourseIcon.ELECTRONICS -> R.string.course_icon_electronics
    CourseIcon.ROBOTICS -> R.string.course_icon_robotics
    CourseIcon.ENERGY -> R.string.course_icon_energy
    CourseIcon.PHYSICS -> R.string.course_icon_physics
    CourseIcon.ASTRONOMY -> R.string.course_icon_astronomy
    CourseIcon.CHEMISTRY -> R.string.course_icon_chemistry
    CourseIcon.BIOLOGY -> R.string.course_icon_biology
    CourseIcon.ENVIRONMENT -> R.string.course_icon_environment
    CourseIcon.AGRICULTURE -> R.string.course_icon_agriculture
    CourseIcon.MEDICINE -> R.string.course_icon_medicine
    CourseIcon.PHARMACY -> R.string.course_icon_pharmacy
    CourseIcon.ECONOMICS -> R.string.course_icon_economics
    CourseIcon.LAW -> R.string.course_icon_law
    CourseIcon.POLITICS -> R.string.course_icon_politics
    CourseIcon.SOCIOLOGY -> R.string.course_icon_sociology
    CourseIcon.EDUCATION -> R.string.course_icon_education
    CourseIcon.LANGUAGE -> R.string.course_icon_language
    CourseIcon.MEDIA -> R.string.course_icon_media
    CourseIcon.LITERATURE -> R.string.course_icon_literature
    CourseIcon.HISTORY -> R.string.course_icon_history
    CourseIcon.PHILOSOPHY -> R.string.course_icon_philosophy
    CourseIcon.THEOLOGY -> R.string.course_icon_theology
    CourseIcon.PSYCHOLOGY -> R.string.course_icon_psychology
    CourseIcon.ENGINEERING -> R.string.course_icon_engineering
    CourseIcon.ARCHITECTURE -> R.string.course_icon_architecture
    CourseIcon.GEOGRAPHY -> R.string.course_icon_geography
    CourseIcon.ART -> R.string.course_icon_art
    CourseIcon.PHOTOGRAPHY -> R.string.course_icon_photography
    CourseIcon.MUSIC -> R.string.course_icon_music
    CourseIcon.SPORTS -> R.string.course_icon_sports
    CourseIcon.LIBRARY -> R.string.course_icon_library
    CourseIcon.PROJECT -> R.string.course_icon_project
    CourseIcon.THESIS -> R.string.course_icon_thesis
    CourseIcon.INTERNSHIP -> R.string.course_icon_internship
    CourseIcon.SEMINAR -> R.string.course_icon_seminar
    CourseIcon.LAB -> R.string.course_icon_lab
    CourseIcon.CALCULATOR -> R.string.course_icon_calculator
    CourseIcon.COMMUNITY -> R.string.course_icon_community
    CourseIcon.BOOK -> R.string.course_icon_book
}

private fun colorLabel(color: CourseColor): Int = when (color) {
    CourseColor.RED -> R.string.course_color_red
    CourseColor.ORANGE -> R.string.course_color_orange
    CourseColor.AMBER -> R.string.course_color_amber
    CourseColor.LIME -> R.string.course_color_lime
    CourseColor.GREEN -> R.string.course_color_green
    CourseColor.TEAL -> R.string.course_color_teal
    CourseColor.CYAN -> R.string.course_color_cyan
    CourseColor.BLUE -> R.string.course_color_blue
    CourseColor.INDIGO -> R.string.course_color_indigo
    CourseColor.PURPLE -> R.string.course_color_purple
    CourseColor.PINK -> R.string.course_color_pink
    CourseColor.BROWN -> R.string.course_color_brown
    CourseColor.GREY -> R.string.course_color_grey
}
