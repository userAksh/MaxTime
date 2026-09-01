package com.maxtime.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maxtime.app.data.StatusChipVariant
import com.maxtime.app.ui.theme.MaxTimeColors
import com.maxtime.app.ui.theme.MaxTimeShapes
import com.maxtime.app.ui.theme.MaxTimeSpacing
import com.maxtime.app.ui.theme.MaxTimeType

/** Soft lime glow behind primary CTAs / active elements — a signature visual detail, not filler. */
@Composable
private fun GlowLayer(shape: Shape, modifier: Modifier = Modifier) {
    Box(
        modifier
            .blur(18.dp)
            .background(MaxTimeColors.glowMid, shape)
    )
}

@Composable
fun PrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        GlowLayer(MaxTimeShapes.medium, Modifier.fillMaxWidth().height(50.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(MaxTimeShapes.medium)
                .background(if (enabled) MaxTimeColors.accent else MaxTimeColors.accent.copy(alpha = 0.4f))
                .clickable(enabled = enabled, onClick = onClick)
                .padding(vertical = 15.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text, style = MaxTimeType.buttonText, color = MaxTimeColors.onAccent, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun SecondaryButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(MaxTimeShapes.medium)
            .background(MaxTimeColors.cardBg)
            .border(1.dp, MaxTimeColors.cardBorder, MaxTimeShapes.medium)
            .clickable(onClick = onClick)
            .padding(vertical = 15.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = MaxTimeType.buttonText, color = MaxTimeColors.textStrong, textAlign = TextAlign.Center)
    }
}

@Composable
fun TextLink(text: String, modifier: Modifier = Modifier, color: Color = MaxTimeColors.accent2, onClick: () -> Unit) {
    Text(
        text,
        modifier = modifier.clickable(onClick = onClick),
        style = MaxTimeType.caption.copy(color = color, fontWeight = FontWeight.Bold)
    )
}

/** Selectable pill chip — muted by default, lime-filled when selected. */
@Composable
fun SelectableChip(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier
            .clip(MaxTimeShapes.pill)
            .background(if (selected) MaxTimeColors.accent else MaxTimeColors.bg2)
            .border(1.dp, if (selected) Color.Transparent else MaxTimeColors.chipBorder, MaxTimeShapes.pill)
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 9.dp)
    ) {
        Text(
            text,
            color = if (selected) MaxTimeColors.onAccent else MaxTimeColors.muted2,
            style = MaxTimeType.caption.copy(fontWeight = FontWeight.SemiBold, color = if (selected) MaxTimeColors.onAccent else MaxTimeColors.muted2)
        )
    }
}

/** Larger selectable interest/intent chip (rounded, filled when on). */
@Composable
fun InterestChip(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(MaxTimeShapes.pill)
            .background(if (selected) MaxTimeColors.accent else MaxTimeColors.cardBg)
            .border(1.dp, if (selected) Color.Transparent else MaxTimeColors.cardBorder, MaxTimeShapes.pill)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp)
    ) {
        Text(
            text,
            color = if (selected) MaxTimeColors.onAccent else MaxTimeColors.text,
            style = MaxTimeType.caption.copy(fontWeight = FontWeight.SemiBold, color = if (selected) MaxTimeColors.onAccent else MaxTimeColors.text)
        )
    }
}

@Composable
fun StatusChip(text: String, variant: StatusChipVariant, modifier: Modifier = Modifier) {
    val (bg, border, fg) = when (variant) {
        StatusChipVariant.FILLING -> Triple(MaxTimeColors.accentWash, MaxTimeColors.accentDim, MaxTimeColors.accent2)
        StatusChipVariant.FULL -> Triple(MaxTimeColors.amberWash, MaxTimeColors.amber.copy(alpha = 0.3f), MaxTimeColors.amber)
        StatusChipVariant.BOOKED -> Triple(MaxTimeColors.accent, Color.Transparent, MaxTimeColors.onAccent)
        StatusChipVariant.CANCELLED -> Triple(MaxTimeColors.redWash, MaxTimeColors.red.copy(alpha = 0.3f), MaxTimeColors.red)
    }
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(text.uppercase(), color = fg, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.4.sp)
    }
}

@Composable
fun SectionLabel(title: String, modifier: Modifier = Modifier, actionText: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaxTimeType.sectionLabel)
        if (actionText != null) {
            TextLink(actionText, onClick = { onAction?.invoke() })
        }
    }
}

@Composable
fun AvatarStack(names: List<String>, modifier: Modifier = Modifier, size: Dp = 24.dp) {
    Row(modifier) {
        names.forEachIndexed { index, name ->
            Box(
                Modifier
                    .offset(x = if (index == 0) 0.dp else (-8).dp * index)
                    .size(size)
                    .clip(CircleShape)
                    .border(2.dp, MaxTimeColors.cardBg, CircleShape)
                    .background(MaxTimeColors.accent),
                contentAlignment = Alignment.Center
            ) {
                if (size >= 32.dp) {
                    Text(name.take(1), color = MaxTimeColors.onAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** A full-width card container matching `.card-bg` / `.card-border`. */
@Composable
fun MaxTimeCard(
    modifier: Modifier = Modifier,
    shape: Shape = MaxTimeShapes.medium,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaxTimeColors.cardBg)
            .border(1.dp, MaxTimeColors.cardBorder, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(15.dp),
        content = content
    )
}

@Composable
fun ProgressBar(current: Int, total: Int, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(total) { index ->
            Box(
                Modifier
                    .weight(1f, fill = true)
                    .height(3.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (index < current) MaxTimeColors.accent else MaxTimeColors.cardBorder)
            )
        }
    }
}

@Composable
fun BackButton(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(MaxTimeColors.cardBg)
            .border(1.dp, MaxTimeColors.cardBorder, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text("←", color = MaxTimeColors.textStrong)
    }
}

@Composable
fun TopBar(title: String = "", modifier: Modifier = Modifier, onBack: (() -> Unit)? = null) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) BackButton(onClick = onBack) else Box(Modifier.size(32.dp))
        Text(title.uppercase(), style = MaxTimeType.metaLabel)
        Box(Modifier.size(32.dp))
    }
}
