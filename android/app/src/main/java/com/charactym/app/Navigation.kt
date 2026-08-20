package com.charactym.app

import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.charactym.app.ui.detail.DetailScreen
import com.charactym.app.ui.editor.EditorScreen
import com.charactym.app.ui.main.MainScreen
import com.charactym.app.ui.manage.ManageScreen

@Composable
fun MainNavigation() {
  val backStack = rememberNavBackStack(Main)

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryDecorators =
      listOf(
        rememberSaveableStateHolderNavEntryDecorator(),
        // 每个导航条目独立 ViewModelStore：避免编辑页串用新建页的状态
        rememberViewModelStoreNavEntryDecorator(),
      ),
    entryProvider =
      entryProvider {
        entry<Main>(clazzContentKey = { key -> key.toString() }) {
          MainScreen(
            onOpenDetail = { id -> backStack.add(Detail(id)) },
            onOpenManage = { backStack.add(Manage) },
          )
        }
        entry<Manage>(clazzContentKey = { key -> key.toString() }) {
          ManageScreen(
            onBack = { backStack.removeLastOrNull() },
            modifier = Modifier.safeDrawingPadding(),
          )
        }
        entry<Detail>(clazzContentKey = { key -> key.toString() }) { key ->
          DetailScreen(
            glyphId = key.glyphId,
            onBack = { backStack.removeLastOrNull() },
            onEdit = { backStack.add(Edit(key.glyphId)) },
            modifier = Modifier.safeDrawingPadding(),
          )
        }
        entry<Edit>(clazzContentKey = { key -> key.toString() }) { key ->
          EditorScreen(
            glyphId = key.glyphId,
            onSaved = { backStack.removeLastOrNull() },
            modifier = Modifier.safeDrawingPadding(),
          )
        }
      },
  )
}
