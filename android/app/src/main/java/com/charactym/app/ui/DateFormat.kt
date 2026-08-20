package com.charactym.app.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 时间戳 → "yyyy-MM-dd"（仅界面展示用） */
fun formatDate(timestamp: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(timestamp))
