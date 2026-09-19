package app.folio.ui.screens.reader

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.AutoFixNormal
import androidx.compose.material.icons.rounded.BorderColor
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.folio.R
import app.folio.core.model.InkStyle
import app.folio.core.model.InkTool
import app.folio.core.model.InkToolStyle
import app.folio.reader.api.ScribbleState

@Composable
fun ScribbleToolbar(
    scribble: ScribbleState,
    undoRedo: UndoRedo,
    onTool: (InkTool) -> Unit,
    onStyle: (InkToolStyle) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onDone: () -> Unit,
    pageLabel: String? = null,
    onPrevious: (() -> Unit)? = null,
    onNext: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val style = scribble.style
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
    ) {
        Column(
            Modifier
                .windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility)
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            if (onPrevious != null && onNext != null) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onPrevious) {
                        Icon(Icons.Rounded.ChevronLeft, contentDescription = stringResource(R.string.scribble_previous_page))
                    }
                    Text(
                        text = pageLabel.orEmpty(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onNext) {
                        Icon(Icons.Rounded.ChevronRight, contentDescription = stringResource(R.string.scribble_next_page))
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                ToolToggle(InkTool.PEN, scribble.tool, onTool) {
                    Icon(Icons.Rounded.Draw, contentDescription = stringResource(R.string.scribble_pen))
                }
                ToolToggle(InkTool.HIGHLIGHTER, scribble.tool, onTool) {
                    Icon(Icons.Rounded.BorderColor, contentDescription = stringResource(R.string.scribble_highlighter))
                }
                ToolToggle(InkTool.ERASER, scribble.tool, onTool) {
                    Icon(Icons.Rounded.AutoFixNormal, contentDescription = stringResource(R.string.scribble_eraser))
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onUndo, enabled = undoRedo.canUndo) {
                    Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = stringResource(R.string.action_undo))
                }
                IconButton(onClick = onRedo, enabled = undoRedo.canRedo) {
                    Icon(Icons.AutoMirrored.Rounded.Redo, contentDescription = stringResource(R.string.scribble_redo))
                }
                TextButton(onClick = onDone) { Text(stringResource(R.string.action_done)) }
            }

            if (scribble.tool != InkTool.ERASER) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    InkStyle.PALETTE.forEachIndexed { index, colour ->
                        ColourSwatch(
                            colour = Color(colour),
                            selected = (style.argb and 0xFFFFFF) == (colour and 0xFFFFFF),
                            label = stringResource(R.string.scribble_colour, index + 1),
                            onClick = { onStyle(style.copy(argb = colour)) },
                        )
                    }
                }
                WidthAndOpacityRow(style = style, tool = scribble.tool, onStyle = onStyle)
            }
        }
    }
}

@Composable
private fun ToolToggle(tool: InkTool, current: InkTool, onTool: (InkTool) -> Unit, icon: @Composable () -> Unit) {
    // The theme's tonal toggle renders backwards here (checked = no container), so the selected
    // state is spelled out explicitly: unchecked is a plain icon, checked is a filled primary circle.
    FilledIconToggleButton(
        checked = tool == current,
        onCheckedChange = { onTool(tool) },
        colors = IconButtonDefaults.filledIconToggleButtonColors(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            checkedContainerColor = MaterialTheme.colorScheme.primary,
            checkedContentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) { icon() }
}

@Composable
private fun ColourSwatch(colour: Color, selected: Boolean, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = label
                this.selected = selected
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(if (selected) 30.dp else 26.dp)
                .clip(CircleShape)
                .background(colour)
                .border(
                    width = if (selected) 3.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    shape = CircleShape,
                ),
        )
    }
}

@Composable
private fun WidthDot(index: Int, selected: Boolean, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = label
                this.selected = selected
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size((6 + index * 5).dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurface),
        )
    }
}

/** Width dots, then the opacity slider (with live preview) sharing the rest of the row. */
@Composable
private fun WidthAndOpacityRow(style: InkToolStyle, tool: InkTool, onStyle: (InkToolStyle) -> Unit) {
    // Local while dragging; saved once when the finger lifts so settings are not rewritten per frame.
    var dragging by remember(style.opacityPercent) { mutableFloatStateOf(style.opacityPercent.toFloat()) }
    val shown = InkStyle.snapOpacity(dragging)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        listOf(R.string.scribble_width_thin, R.string.scribble_width_medium, R.string.scribble_width_thick)
            .forEachIndexed { index, label ->
                WidthDot(
                    index = index,
                    selected = style.widthIndex == index,
                    label = stringResource(label),
                    onClick = { onStyle(style.copy(widthIndex = index)) },
                )
            }
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.scribble_opacity, shown),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(start = 8.dp),
            )
            Slider(
                value = dragging,
                onValueChange = { dragging = it },
                onValueChangeFinished = { onStyle(style.copy(opacityPercent = InkStyle.snapOpacity(dragging))) },
                valueRange = InkStyle.MIN_OPACITY.toFloat()..InkStyle.MAX_OPACITY.toFloat(),
                steps = (InkStyle.MAX_OPACITY - InkStyle.MIN_OPACITY) / InkStyle.OPACITY_STEP - 1,
            )
        }
        val previewColour = Color(InkStyle.strokeArgb(style.copy(opacityPercent = shown)))
        val strokeWidth = if (tool == InkTool.HIGHLIGHTER) 12.dp else (2 + style.widthIndex * 2).dp
        Box(
            Modifier
                .padding(horizontal = 8.dp)
                .size(width = 48.dp, height = 28.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.White),
        ) {
            Canvas(Modifier.size(width = 48.dp, height = 28.dp)) {
                drawLine(
                    color = Color(0xFF444444),
                    start = Offset(size.width * 0.2f, size.height / 2f),
                    end = Offset(size.width * 0.8f, size.height / 2f),
                    strokeWidth = 1.dp.toPx(),
                )
                drawLine(
                    color = previewColour,
                    start = Offset(6.dp.toPx(), size.height / 2f),
                    end = Offset(size.width - 6.dp.toPx(), size.height / 2f),
                    strokeWidth = strokeWidth.toPx(),
                    cap = if (tool == InkTool.HIGHLIGHTER) StrokeCap.Square else StrokeCap.Round,
                )
            }
        }
    }
    Spacer(Modifier.height(2.dp))
}
