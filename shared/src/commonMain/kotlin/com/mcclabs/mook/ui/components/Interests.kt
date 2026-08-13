package com.mcclabs.mook.ui.components

import androidx.compose.runtime.Composable
import mook.shared.generated.resources.Res
import mook.shared.generated.resources.interest_travel
import mook.shared.generated.resources.interest_music
import mook.shared.generated.resources.interest_tech
import mook.shared.generated.resources.interest_cooking
import mook.shared.generated.resources.interest_art
import mook.shared.generated.resources.interest_fitness
import org.jetbrains.compose.resources.stringResource

/**
 * Localizes an interest for display. Interests are stored as canonical English values
 * (shared across apps), so this maps a known value to its localized label and falls
 * back to the raw value for anything not in the predefined set.
 */
@Composable
fun interestLabel(interest: String): String = when (interest) {
    "Travel" -> stringResource(Res.string.interest_travel)
    "Music" -> stringResource(Res.string.interest_music)
    "Tech" -> stringResource(Res.string.interest_tech)
    "Cooking" -> stringResource(Res.string.interest_cooking)
    "Art" -> stringResource(Res.string.interest_art)
    "Fitness" -> stringResource(Res.string.interest_fitness)
    else -> interest
}
