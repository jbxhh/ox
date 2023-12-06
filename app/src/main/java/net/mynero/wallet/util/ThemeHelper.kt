/*
 * Copyright (c) 2019 m2049r
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.mynero.wallet.util

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.preference.PreferenceManager
import android.util.TypedValue
import androidx.annotation.ColorInt
import net.mynero.wallet.R

object ThemeHelper {
    fun getThemedResourceId(ctx: Context, attrId: Int): Int {
        val typedValue = TypedValue()
        return if (ctx.theme.resolveAttribute(
                attrId,
                typedValue,
                true
            )
        ) typedValue.resourceId else 0
    }

    @JvmStatic
    @ColorInt
    fun getThemedColor(ctx: Context, attrId: Int): Int {
        val typedValue = TypedValue()
        return if (ctx.theme.resolveAttribute(
                attrId,
                typedValue,
                true
            )
        ) typedValue.data else Color.BLACK
    }

    fun setTheme(activity: Activity, theme: String) {
        activity.setTheme(R.style.MyMaterialThemeOled)
    }

    fun setPreferred(activity: Activity) {
        val theme = PreferenceManager.getDefaultSharedPreferences(activity)
            .getString(activity.getString(R.string.preferred_theme), "Classic")
        theme?.let { setTheme(activity, it) }
    }
}