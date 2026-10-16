package com.kodastream.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Loading placeholders that stand in for content at its real size and shape,
 * so nothing reflows when the data lands. The screen's chrome never waits and
 * is never replaced by a spinner - only the regions actually blocked on data.
 *
 * A slow alpha pulse rather than a travelling gradient: it reads as "not
 * ready" without competing with the staggered entrances the real content
 * plays on arrival. Time-driven, so tween rather than spring.
 */
private const val SKELETON_PULSE_MS = 900

/**
 * The shared pulse. Hoist this once per screen and pass it down so every
 * placeholder breathes in step - separate transitions drift apart and the
 * screen starts to twinkle.
 */
@Composable
fun rememberSkeletonAlpha(): Float {
    val transition = rememberInfiniteTransition(label = "skeletonPulse")
    val alpha by transition.animateFloat(
        initialValue = 0.28f,
        targetValue = 0.62f,
        animationSpec = infiniteRepeatable(
            animation = tween(SKELETON_PULSE_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "skeletonAlpha"
    )
    return alpha
}

/**
 * A single placeholder block. Size it with [modifier] to match whatever it is
 * standing in for; [shape] should match too, so the swap to real content is a
 * fill rather than a reflow.
 */
@Composable
fun SkeletonBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    alpha: Float = rememberSkeletonAlpha()
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = alpha))
    )
}

/** Placeholder for a line of text, sized like the type it replaces. */
@Composable
fun SkeletonTextLine(
    width: Dp,
    height: Dp,
    modifier: Modifier = Modifier,
    alpha: Float = rememberSkeletonAlpha()
) {
    SkeletonBox(
        modifier = modifier.size(width = width, height = height),
        shape = RoundedCornerShape(height / 2),
        alpha = alpha
    )
}

/**
 * Stand-in for a [PosterCard]: the image at the card's real aspect and width,
 * then two title lines on the card body. Mirrors the real card's shape and
 * padding so the swap does not reflow.
 */
@Composable
fun PosterCardSkeleton(
    modifier: Modifier = Modifier,
    horizontal: Boolean = false,
    alpha: Float = rememberSkeletonAlpha()
) {
    val width = if (horizontal) 220.dp else 128.dp
    val ratio = if (horizontal) 16f / 9f else 2f / 3f
    Surface(
        modifier = modifier.width(width),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 1.dp
    ) {
        Column {
            SkeletonBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(ratio),
                alpha = alpha
            )
            Column(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SkeletonBox(
                    modifier = Modifier.fillMaxWidth().height(14.dp),
                    shape = RoundedCornerShape(7.dp),
                    alpha = alpha
                )
                SkeletonBox(
                    modifier = Modifier.fillMaxWidth(0.55f).height(14.dp),
                    shape = RoundedCornerShape(7.dp),
                    alpha = alpha
                )
            }
        }
    }
}

/**
 * A run of identical placeholders, spaced like the list they stand in for.
 *
 * Takes the whole run rather than one row because these are emitted from a
 * single `LazyColumn` item: the placeholder count is fixed, so there is
 * nothing for lazy layout to save, and one item keeps the pulse hoisted once
 * for the whole group instead of per row.
 */
@Composable
fun SkeletonList(
    count: Int,
    modifier: Modifier = Modifier,
    spacing: Dp = 16.dp,
    row: @Composable (alpha: Float) -> Unit
) {
    val alpha = rememberSkeletonAlpha()
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing)
    ) {
        repeat(count) { row(alpha) }
    }
}

/**
 * A run of poster placeholders standing in for a shelf row, one screen-wide
 * `LazyRow` item while the real row is loading.
 */
@Composable
fun PosterShelfSkeleton(
    count: Int = 4,
    modifier: Modifier = Modifier,
    horizontal: Boolean = false,
) {
    val alpha = rememberSkeletonAlpha()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        repeat(count) {
            PosterCardSkeleton(horizontal = horizontal, alpha = alpha)
        }
    }
}

/**
 * Stand-in for an episode row: a 120dp-wide 16:9 thumbnail beside a title and
 * a metadata line. Also the right shape for any thumbnail-plus-two-lines row.
 */
@Composable
fun ThumbRowSkeleton(
    modifier: Modifier = Modifier,
    alpha: Float = rememberSkeletonAlpha()
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SkeletonBox(
                modifier = Modifier
                    .width(120.dp)
                    .aspectRatio(16f / 9f),
                shape = RoundedCornerShape(10.dp),
                alpha = alpha
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SkeletonTextLine(width = 150.dp, height = 14.dp, alpha = alpha)
                SkeletonTextLine(width = 80.dp, height = 11.dp, alpha = alpha)
            }
        }
    }
}
