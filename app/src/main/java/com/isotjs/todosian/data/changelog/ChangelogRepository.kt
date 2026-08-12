package com.isotjs.todosian.data.changelog

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.FilterCenterFocus
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SubdirectoryArrowRight
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.ui.graphics.vector.ImageVector
import org.json.JSONArray

import com.isotjs.todosian.BuildConfig

data class ChangelogContributor(
    val name: String,
    val url: String,
)

data class ChangelogEntry(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val contributor: ChangelogContributor? = null,
)

data class ChangelogVersion(
    val versionCode: Int,
    val versionName: String,
    val items: List<ChangelogEntry>,
)

object ChangelogRepository {
    fun getChangelogForVersion(context: Context, targetVersionCode: Int): ChangelogVersion? {
        val allVersions = loadChangelogs(context)
        return allVersions.find { it.versionCode == targetVersionCode } ?: allVersions.maxByOrNull { it.versionCode }
    }

    private fun loadChangelogs(context: Context): List<ChangelogVersion> {
        return runCatching {
            val jsonString = context.assets.open("changelog.json").bufferedReader().use { it.readText() }
            val jsonArray = JSONArray(jsonString)
            val result = mutableListOf<ChangelogVersion>()

            for (i in 0 until jsonArray.length()) {
                val versionObj = jsonArray.getJSONObject(i)
                val versionCode = versionObj.optInt("versionCode", 0)
                val versionName = versionObj.optString("versionName", "").ifBlank { BuildConfig.VERSION_NAME }

                val itemsArray = versionObj.optJSONArray("items") ?: JSONArray()
                val itemList = mutableListOf<ChangelogEntry>()

                for (j in 0 until itemsArray.length()) {
                    val itemObj = itemsArray.getJSONObject(j)
                    val iconName = itemObj.optString("icon", "")
                    val title = itemObj.optString("title", "")
                    val description = itemObj.optString("description", "")

                    val contributorObj = itemObj.optJSONObject("contributor")
                    val contributor = if (contributorObj != null) {
                        ChangelogContributor(
                            name = contributorObj.optString("name", ""),
                            url = contributorObj.optString("url", ""),
                        ).takeIf { it.name.isNotBlank() && it.url.isNotBlank() }
                    } else {
                        null
                    }

                    itemList.add(
                        ChangelogEntry(
                            icon = parseIcon(iconName),
                            title = title,
                            description = description,
                            contributor = contributor,
                        )
                    )
                }

                result.add(
                    ChangelogVersion(
                        versionCode = versionCode,
                        versionName = versionName,
                        items = itemList,
                    )
                )
            }
            result
        }.getOrDefault(emptyList())
    }

    private fun parseIcon(name: String): ImageVector {
        return when (name) {
            "FilterCenterFocus" -> Icons.Filled.FilterCenterFocus
            "AutoAwesome" -> Icons.Filled.AutoAwesome
            "Checklist" -> Icons.Filled.Checklist
            "SwapVert" -> Icons.Filled.SwapVert
            "Notifications" -> Icons.Filled.Notifications
            "SubdirectoryArrowRight" -> Icons.Filled.SubdirectoryArrowRight
            "Swipe" -> Icons.Filled.Swipe
            "Palette" -> Icons.Filled.Palette
            "Speed" -> Icons.Filled.Speed
            "BugReport" -> Icons.Filled.BugReport
            "Stars" -> Icons.Filled.Stars
            "Widgets" -> Icons.Filled.Widgets
            else -> Icons.Filled.Info
        }
    }
}
