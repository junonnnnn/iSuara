package com.isuara.app.emotion.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.isuara.app.emotion.EmotionLabel
import com.isuara.app.emotion.EmotionReading
import com.isuara.app.ui.googleSansFlex

/**
 * The live facial-expression readout, styled to sit beside the FPS badge.
 *
 * Deliberately small and unobtrusive. It exists so the signer can tell that the
 * app noticed their expression — and so a wrong reading is visible rather than
 * silently steering the translation — not to be a headline feature.
 *
 * Renders nothing when [reading] is null, which is the honest display for "no
 * face in frame". Showing a neutral chip instead would assert calm we never
 * observed.
 */
@Composable
fun EmotionChip(reading: EmotionReading?, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = reading != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        // Held so the chip fades out with its last colour rather than snapping
        // to the default as the reading clears.
        val label = reading?.label ?: EmotionLabel.NEUTRAL
        val target = accentFor(label)
        val accent by animateColorAsState(target, label = "emotionAccent")
        val alpha = if (reading?.isStale == true) 0.45f else 1f

        Surface(
            color = Color.Black.copy(alpha = 0.5f),
            shape = RoundedCornerShape(16.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = alpha))
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = label.descriptorEn.uppercase(),
                    fontFamily = googleSansFlex,
                    color = Color.White.copy(alpha = alpha),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/**
 * Colour by what the expression means for the listener, not by which of the
 * eight classes it is: urgency reads red, warmth amber, low mood violet. Three
 * meanings are legible at a glance on a 8dp dot; eight would not be.
 */
private fun accentFor(label: EmotionLabel): Color = when {
    label == EmotionLabel.NEUTRAL -> Color(0xFF8B949E)
    label.isHighArousal && label.valence < 0f -> Color(0xFFF44336)
    label.isHighArousal -> Color(0xFFFFC107)
    else -> Color(0xFF7E57C2)
}
