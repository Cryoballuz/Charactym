package com.charactym.app

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Main : NavKey

@Serializable data object Manage : NavKey

@Serializable data class Detail(val glyphId: Long) : NavKey

@Serializable data class Edit(val glyphId: Long) : NavKey
