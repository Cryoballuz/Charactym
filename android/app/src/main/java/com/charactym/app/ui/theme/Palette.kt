package com.charactym.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Charactym 设计规范色板（淡蓝 - 白 - 灰，见 docs/03-design-spec.md）。
 * 阶段 6 将把整套配色整合进 Material3 主题。
 */
object Palette {
    val BluePrimary = Color(0xFF5B8BC0)
    val BlueLight = Color(0xFF7FA8D9)
    val DangerRed = Color(0xFFC96A6A)
    val GrayText = Color(0xFF8A94A3)
    val BorderGray = Color(0xFFD9DEE6)
    val MainText = Color(0xFF333333)
    val SuccessGreen = Color(0xFF2E7D32)
    val PageBackground = Color(0xFFF7F9FB)

    /** 映射页：无对应字的淡红色占位方块 */
    val PlaceholderRed = Color(0xFFF2C4C4)

    /** 映射页：一个字符对应多个字时的淡蓝蒙版 */
    val VariantMaskBlue = Color(0x407FA8D9)
}
