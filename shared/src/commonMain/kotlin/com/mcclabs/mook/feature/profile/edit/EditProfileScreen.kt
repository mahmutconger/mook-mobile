package com.mcclabs.mook.feature.profile.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.mcclabs.mook.ui.theme.NeonColors
import com.mcclabs.mook.util.rememberGalleryPicker
import mook.shared.generated.resources.Res
import mook.shared.generated.resources.edit_profile_title
import mook.shared.generated.resources.edit_profile_photos_title
import mook.shared.generated.resources.edit_profile_photos_hint
import mook.shared.generated.resources.edit_profile_move_left_cd
import mook.shared.generated.resources.edit_profile_move_right_cd
import mook.shared.generated.resources.common_back
import mook.shared.generated.resources.common_save
import mook.shared.generated.resources.common_add
import mook.shared.generated.resources.common_delete
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.mcclabs.mook.domain.model.Languages
import com.mcclabs.mook.ui.components.CustomAuthTextField
import com.mcclabs.mook.ui.components.LanguageDropdown
import com.mcclabs.mook.ui.components.CountryDropdown
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import mook.shared.generated.resources.edit_profile_basic_info_title
import mook.shared.generated.resources.edit_profile_name_label
import mook.shared.generated.resources.edit_profile_bio_label
import mook.shared.generated.resources.edit_profile_birthdate_label
import mook.shared.generated.resources.edit_profile_birthdate_placeholder
import mook.shared.generated.resources.edit_profile_language_label
import mook.shared.generated.resources.edit_profile_country_label
import mook.shared.generated.resources.registration_date_picker_ok
import mook.shared.generated.resources.registration_date_picker_cancel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    onNavigateBack: () -> Unit,
    viewModel: EditProfileViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    
    val photoPicker = rememberGalleryPicker(onImagePicked = { uri ->
        viewModel.addPhoto(uri)
    })

    var showDatePicker by remember { mutableStateOf(false) }
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = state.birthDateMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onBirthDateChange(datePickerState.selectedDateMillis)
                    showDatePicker = false
                }) { Text(stringResource(Res.string.registration_date_picker_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(Res.string.registration_date_picker_cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is EditProfileEvent.NavigateBack -> onNavigateBack()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.edit_profile_title), color = NeonColors.TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.common_back),
                            tint = NeonColors.TextPrimary
                        )
                    }
                },
                actions = {
                    if (state.hasUnsavedChanges && !state.isSaving) {
                        Button(
                            onClick = viewModel::saveChanges,
                            colors = ButtonDefaults.buttonColors(containerColor = NeonColors.Primary),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text(stringResource(Res.string.common_save), color = NeonColors.Background, fontWeight = FontWeight.Bold)
                        }
                    } else if (state.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(end = 16.dp).size(24.dp),
                            color = NeonColors.Primary,
                            strokeWidth = 2.dp
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NeonColors.Background)
            )
        },
        containerColor = NeonColors.Background
    ) { padding ->
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NeonColors.Primary)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // ---- Basic info form (spans the full grid width) -------------
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = stringResource(Res.string.edit_profile_basic_info_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = NeonColors.TextPrimary
                        )

                        CustomAuthTextField(
                            value = state.displayName,
                            onValueChange = viewModel::onNameChange,
                            label = stringResource(Res.string.edit_profile_name_label),
                            isError = state.displayNameError != null,
                            errorMessage = state.displayNameError
                        )

                        CustomAuthTextField(
                            value = state.bio,
                            onValueChange = viewModel::onBioChange,
                            label = stringResource(Res.string.edit_profile_bio_label),
                            singleLine = false,
                            minLines = 3
                        )

                        // Birth date (opens the calendar picker; age is derived from it)
                        Column {
                            Text(
                                text = stringResource(Res.string.edit_profile_birthdate_label),
                                style = MaterialTheme.typography.labelMedium,
                                color = NeonColors.TextSecondary
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(NeonColors.InputBackground)
                                    .clickable { showDatePicker = true }
                                    .padding(horizontal = 16.dp, vertical = 16.dp)
                            ) {
                                Text(
                                    text = state.birthDateMillis?.let { formatBirthDate(it) }
                                        ?: stringResource(Res.string.edit_profile_birthdate_placeholder),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (state.birthDateMillis != null) NeonColors.TextPrimary else NeonColors.TextTertiary
                                )
                            }
                        }

                        LanguageDropdown(
                            label = stringResource(Res.string.edit_profile_language_label),
                            selectedLanguage = state.selectedLanguage,
                            onLanguageSelected = viewModel::onLanguageChange,
                            languages = Languages.ALL
                        )

                        CountryDropdown(
                            label = stringResource(Res.string.edit_profile_country_label),
                            selectedCountry = state.selectedCountry,
                            onCountrySelected = viewModel::onCountryChange,
                            countries = state.availableCountries
                        )

                        Spacer(Modifier.height(8.dp))

                        Text(
                            text = stringResource(Res.string.edit_profile_photos_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = NeonColors.TextPrimary
                        )
                        Text(
                            text = stringResource(Res.string.edit_profile_photos_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = NeonColors.TextSecondary
                        )
                    }
                }

                itemsIndexed(state.photos) { index, photoUrl ->
                    PhotoEditCard(
                        url = photoUrl,
                        isFirst = index == 0,
                        isLast = index == state.photos.size - 1,
                        onMoveLeft = { viewModel.movePhoto(index, index - 1) },
                        onMoveRight = { viewModel.movePhoto(index, index + 1) },
                        onDelete = { viewModel.deletePhoto(index) }
                    )
                }

                // Add Photo button if less than max allowed (e.g., 6)
                if (state.photos.size < 6) {
                    item {
                        AddPhotoCard(onClick = { photoPicker.launch() })
                    }
                }

                if (state.error != null) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = state.error!!,
                            color = NeonColors.Error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PhotoEditCard(
    url: String,
    isFirst: Boolean,
    isLast: Boolean,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onDelete: () -> Unit
) {
    Box(
        modifier = Modifier
            .aspectRatio(3f/4f)
            .clip(RoundedCornerShape(8.dp))
            .background(NeonColors.CardBorder)
    ) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        
        // Delete badge (top right)
        IconButton(
            onClick = onDelete,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(24.dp)
                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(Res.string.common_delete),
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }
        
        // Directional arrows (bottom center)
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            if (!isFirst) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(Res.string.edit_profile_move_left_cd),
                    tint = Color.White,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable { onMoveLeft() }
                )
            } else {
                Spacer(modifier = Modifier.size(20.dp))
            }
            
            if (!isLast) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = stringResource(Res.string.edit_profile_move_right_cd),
                    tint = Color.White,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable { onMoveRight() }
                )
            } else {
                Spacer(modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** Formats a birth date (epoch millis) as an ISO date, e.g. "2000-01-15". */
private fun formatBirthDate(millis: Long): String =
    Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.UTC).date.toString()

@Composable
fun AddPhotoCard(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .aspectRatio(3f/4f)
            .clip(RoundedCornerShape(8.dp))
            .background(NeonColors.Card)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = stringResource(Res.string.common_add),
                tint = NeonColors.Primary,
                modifier = Modifier.size(32.dp)
            )
            Text(
                text = stringResource(Res.string.common_add),
                color = NeonColors.Primary,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
