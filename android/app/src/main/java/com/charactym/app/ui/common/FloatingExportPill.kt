package com.charactym.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.charactym.app.ui.theme.Palette

/**
 * 多选模式下的浮动胶囊：显示已选数量，提供取消与导出 PNG 按钮。
 */
@Composable
fun FloatingExportPill(
    count: Int,
    onCancel: () -> Unit,
    onExport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Palette.BluePrimary)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onCancel) {
            Text("取消", color = Color.White, fontSize = 13.sp)
        }
        Text(
            "已选 $count 张",
            color = Color.White,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        Button(
            onClick = onExport,
            enabled = count > 0,
            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            shape = RoundedCornerShape(18.dp),
        ) {
            Text("导出 PNG", color = Palette.BluePrimary, fontSize = 13.sp)
        }
    }
}
