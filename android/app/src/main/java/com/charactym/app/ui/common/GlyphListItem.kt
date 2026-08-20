package com.charactym.app.ui.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.charactym.app.CharactymApp
import com.charactym.app.data.local.Glyph
import com.charactym.app.ui.formatDate
import com.charactym.app.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 浏览/搜索共用的列表条目：缩略图 + 汉字 + 备注摘要 + 日期。
 * 多选模式下：选中显示蓝色边框；长按进入多选（由外层处理）。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GlyphListItem(
    glyph: Glyph,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val repository = remember { CharactymApp.from(context).glyphRepository }
    val thumb by produceState<ImageBitmap?>(null, glyph.id, glyph.pngPath) {
        value = withContext(Dispatchers.IO) { repository.loadBitmap(glyph.id)?.asImageBitmap() }
    }
    val shape = RoundedCornerShape(8.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) Palette.BlueLight.copy(alpha = 0.12f) else Color.White)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) Palette.BluePrimary else Palette.BorderGray,
                shape = shape,
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(Color.White)
                .border(1.dp, Palette.BorderGray),
        ) {
            thumb?.let { Image(bitmap = it, contentDescription = "缩略图", modifier = Modifier.fillMaxSize()) }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            // 无映射字的记录：映射字位置留空，备注照常显示
            if (glyph.hanzi.isNotEmpty()) {
                Text(glyph.hanzi, fontSize = 18.sp, color = Palette.MainText)
            }
            if (glyph.note.isNotBlank()) {
                Text(
                    glyph.note,
                    fontSize = 12.sp,
                    color = Palette.GrayText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(formatDate(glyph.createdAt), fontSize = 11.sp, color = Palette.GrayText)
    }
}
