package com.iven.besimple.helpers

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue
import android.widget.ImageButton
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import com.iven.besimple.BeSimpleConstants
import com.iven.besimple.R
import com.iven.besimple.beSimplePreferences
import com.iven.besimple.player.MediaPlayerHolder
import com.iven.besimple.ui.MainActivity


object ThemeHelper {

    @JvmStatic
    fun applyChanges(activity: Activity) {
        val intent = Intent(activity, MainActivity::class.java)

        intent.addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP
                        or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        or Intent.FLAG_ACTIVITY_NEW_TASK
        )
        activity.run {
            finishAfterTransition()
            startActivity(intent)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }

    @JvmStatic
    fun getDefaultNightMode(context: Context) = when (beSimplePreferences.theme) {
        context.getString(R.string.theme_pref_light) -> AppCompatDelegate.MODE_NIGHT_NO
        context.getString(R.string.theme_pref_dark) -> AppCompatDelegate.MODE_NIGHT_YES
        else -> if (VersioningHelper.isQ()) {
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        } else {
            AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY
        }
    }

    @JvmStatic
    fun resolveThemeIcon(context: Context) = when (beSimplePreferences.theme) {
        context.getString(R.string.theme_pref_light) -> R.drawable.ic_day
        context.getString(R.string.theme_pref_auto) -> R.drawable.ic_auto
        else -> R.drawable.ic_night
    }

    fun resolveSortAlbumSongsIcon(sort: Int): Int {
        return when (sort) {
            BeSimpleConstants.ASCENDING_SORTING -> R.drawable.ic_sort_alphabetical_descending
            BeSimpleConstants.DESCENDING_SORTING -> R.drawable.ic_sort_alphabetical_ascending
            BeSimpleConstants.TRACK_SORTING -> R.drawable.ic_sort_numeric_descending
            else -> R.drawable.ic_sort_numeric_ascending
        }
    }

    @JvmStatic
    fun isDeviceLand(resources: Resources) =
            resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    @JvmStatic
    fun updateIconTint(imageButton: ImageButton, tint: Int) {
        ImageViewCompat.setImageTintList(
                imageButton, ColorStateList.valueOf(tint)
        )
    }

    @ColorInt
    @JvmStatic
    fun resolveColorAttr(context: Context, @AttrRes colorAttr: Int): Int {
        val resolvedAttr: TypedValue =
                resolveThemeAttr(
                        context,
                        colorAttr
                )
        // resourceId is used if it's a ColorStateList, and data if it's a color reference or a hex color
        val colorRes =
                if (resolvedAttr.resourceId != 0) {
                    resolvedAttr.resourceId
                } else {
                    resolvedAttr.data
                }
        return ContextCompat.getColor(context, colorRes)
    }

    @JvmStatic
    private fun resolveThemeAttr(context: Context, @AttrRes attrRes: Int) =
            TypedValue().apply { context.theme.resolveAttribute(attrRes, this, true) }

    @JvmStatic
    fun createColouredRipple(context: Context, rippleColor: Int, rippleId: Int): Drawable {
        val ripple = AppCompatResources.getDrawable(context, rippleId) as RippleDrawable
        return ripple.apply {
            setColor(ColorStateList.valueOf(rippleColor))
        }
    }

    @JvmStatic
    fun getRepeatIcon(mediaPlayerHolder: MediaPlayerHolder) = when {
        mediaPlayerHolder.isRepeat1X -> R.drawable.ic_repeat_one
        mediaPlayerHolder.isLooping -> R.drawable.ic_repeat
        else -> R.drawable.ic_repeat_one_notif_disabled
    }
}
