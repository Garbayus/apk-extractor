package com.example.apkextractor.ui.main

import android.app.Application
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.activity.ComponentActivity
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.example.apkextractor.data.AppInfo
import com.example.apkextractor.data.DataRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test

class MainScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun fakeApp_isDisplayed() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val repository = object : DataRepository {
            override fun getApps(context: Context): Flow<List<AppInfo>> = flowOf(listOf(fakeApp()))
        }

        composeTestRule.setContent {
            val viewModel = remember { MainScreenViewModel(application, repository) }
            MainScreen(
                onItemClick = {},
                viewModel = viewModel
            )
        }

        composeTestRule.onNodeWithText("Sample Split App").assertIsDisplayed()
        composeTestRule.onNodeWithText("com.example.sample").assertIsDisplayed()
    }

    private fun fakeApp(): AppInfo {
        return AppInfo(
            name = "Sample Split App",
            packageName = "com.example.sample",
            versionName = "1.0",
            versionCode = 1,
            size = "12.0 MB",
            isSystem = false,
            isPlayStore = true,
            sourceDir = "/data/app/com.example.sample/base.apk",
            splitSourceDirs = listOf("/data/app/com.example.sample/split_config.arm64_v8a.apk"),
            splitNames = listOf("config.arm64_v8a"),
            supportedAbis = listOf("arm64-v8a"),
            minSdk = 23,
            targetSdk = 34,
            icon = ColorDrawable(Color.BLUE)
        )
    }
}
