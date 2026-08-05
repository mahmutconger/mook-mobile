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
import org.koin.compose.viewmodel.koinViewModel

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
                title = { Text("Profili Düzenle", color = NeonColors.TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Geri",
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
                            Text("Kaydet", color = NeonColors.Background, fontWeight = FontWeight.Bold)
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                Text(
                    text = "Fotoğraflar",
                    style = MaterialTheme.typography.titleMedium,
                    color = NeonColors.TextPrimary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                Text(
                    text = "En az 2 fotoğraf yüklemelisin. İlk sıradaki fotoğraf ana profil fotoğrafın olur.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NeonColors.TextSecondary,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                )

                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
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
                }
                
                if (state.error != null) {
                    Text(
                        text = state.error!!,
                        color = NeonColors.Error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
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
                contentDescription = "Sil",
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
                    contentDescription = "Sola Kaydır",
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
                    contentDescription = "Sağa Kaydır",
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
                contentDescription = "Ekle",
                tint = NeonColors.Primary,
                modifier = Modifier.size(32.dp)
            )
            Text(
                text = "Ekle",
                color = NeonColors.Primary,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
