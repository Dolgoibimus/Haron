package com.vamp.haron.presentation.matrix

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vamp.haron.R
import com.vamp.haron.common.util.swipeBackFromLeft

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemesScreen(
    onBack: () -> Unit,
    onOpenMatrix: () -> Unit,
    onOpenSnowfall: () -> Unit,
    onOpenStarfield: () -> Unit = {},
    onOpenDust: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.themes_title), fontSize = 20.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                expandedHeight = 36.dp
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .swipeBackFromLeft(onBack = onBack)
                .padding(padding)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Matrix Rain card
                    ThemeCard(
                        title = stringResource(R.string.matrix_rain_title),
                        modifier = Modifier.weight(1f),
                        onClick = onOpenMatrix
                    ) {
                        MatrixRainCanvas(
                            config = MatrixRainConfig(
                                enabled = true,
                                color = Color(0xFF00FF00),
                                speed = 0.4f,
                                density = 0.6f,
                                opacity = 0.8f,
                                charset = "katakana"
                            )
                        )
                    }

                    // Snowfall card
                    ThemeCard(
                        title = stringResource(R.string.snowfall_title),
                        modifier = Modifier.weight(1f),
                        onClick = onOpenSnowfall
                    ) {
                        SnowfallCanvas(
                            config = SnowfallConfig(enabled = true, speed = 0.4f)
                        )
                    }
                }
            }
            // Row 2: Starfield + Smoke
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ThemeCard(
                        title = stringResource(R.string.starfield_title),
                        modifier = Modifier.weight(1f),
                        onClick = onOpenStarfield
                    ) {
                        StarfieldCanvas(config = StarfieldConfig(enabled = true, speed = 0.4f, density = 0.5f, opacity = 0.8f))
                    }
                    ThemeCard(
                        title = stringResource(R.string.dust_title),
                        modifier = Modifier.weight(1f),
                        onClick = onOpenDust
                    ) {
                        DustCanvas(config = DustConfig(enabled = true, speed = 0.4f, density = 0.5f, opacity = 0.7f))
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeCard(
    title: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    preview: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .height(60.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black)
            .clickable(onClick = onClick)
    ) {
        preview()
        Text(
            text = title,
            color = Color.White,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(vertical = 2.dp)
        )
    }
}
