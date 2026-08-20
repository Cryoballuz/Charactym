package com.charactym.app.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.charactym.app.ui.browse.BrowseScreen
import com.charactym.app.ui.editor.EditorScreen
import com.charactym.app.ui.mapping.MappingScreen
import com.charactym.app.ui.search.SearchScreen
import com.charactym.app.ui.theme.Palette

/**
 * 主页面：底部导航「录入 / 搜索 / 浏览 / 映射」。
 * 「管理」入口在浏览页右上角。切换页签时编辑器状态保留。
 */
@Composable
fun MainScreen(
    onOpenDetail: (Long) -> Unit,
    onOpenManage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        modifier = modifier,
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Text("✏️") },
                    label = { Text("录入") },
                    colors = navItemColors(),
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Text("🔍") },
                    label = { Text("搜索") },
                    colors = navItemColors(),
                )
                NavigationBarItem(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    icon = { Text("📚") },
                    label = { Text("浏览") },
                    colors = navItemColors(),
                )
                NavigationBarItem(
                    selected = tab == 3,
                    onClick = { tab = 3 },
                    icon = { Text("📜") },
                    label = { Text("映射") },
                    colors = navItemColors(),
                )
            }
        },
    ) { padding ->
        val bottomInset = padding.calculateBottomPadding()
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                0 -> EditorScreen()
                1 -> SearchScreen(onOpenDetail = onOpenDetail, bottomInset = bottomInset)
                2 -> BrowseScreen(onOpenDetail = onOpenDetail, onOpenManage = onOpenManage, bottomInset = bottomInset)
                3 -> MappingScreen(bottomInset = bottomInset)
            }
        }
    }
}

@Composable
private fun navItemColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = Palette.BluePrimary,
    selectedTextColor = Palette.BluePrimary,
    unselectedIconColor = Palette.GrayText,
    unselectedTextColor = Palette.GrayText,
    indicatorColor = Palette.BlueLight.copy(alpha = 0.25f),
)
