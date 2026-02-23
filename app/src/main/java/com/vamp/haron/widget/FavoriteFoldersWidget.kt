package com.vamp.haron.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.vamp.haron.MainActivity
import com.vamp.haron.R
import com.vamp.haron.common.constants.HaronConstants
import org.json.JSONArray

class FavoriteFoldersWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val favorites = loadFavorites(context)
        provideContent {
            GlanceTheme {
                WidgetContent(favorites = favorites, context = context)
            }
        }
    }

    private fun loadFavorites(context: Context): List<String> {
        val prefs = context.getSharedPreferences(HaronConstants.PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString("favorites", null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Composable
    private fun WidgetContent(favorites: List<String>, context: Context) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.surface)
                .padding(8.dp)
        ) {
            Text(
                text = context.getString(R.string.widget_title),
                style = TextStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = GlanceTheme.colors.onSurface
                ),
                modifier = GlanceModifier.padding(bottom = 4.dp)
            )

            if (favorites.isEmpty()) {
                Text(
                    text = context.getString(R.string.widget_no_favorites),
                    style = TextStyle(
                        fontSize = 12.sp,
                        color = GlanceTheme.colors.onSurfaceVariant
                    )
                )
            } else {
                LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                    items(favorites) { path ->
                        val intent = Intent(context, MainActivity::class.java).apply {
                            putExtra("navigate_to", path)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }
                        Row(
                            modifier = GlanceModifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp, horizontal = 4.dp)
                                .clickable(actionStartActivity(intent)),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "\uD83D\uDCC1",
                                style = TextStyle(fontSize = 16.sp)
                            )
                            Spacer(GlanceModifier.width(6.dp))
                            Column {
                                Text(
                                    text = path.substringAfterLast('/'),
                                    style = TextStyle(
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = GlanceTheme.colors.onSurface
                                    ),
                                    maxLines = 1
                                )
                                Text(
                                    text = path,
                                    style = TextStyle(
                                        fontSize = 10.sp,
                                        color = GlanceTheme.colors.onSurfaceVariant
                                    ),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
