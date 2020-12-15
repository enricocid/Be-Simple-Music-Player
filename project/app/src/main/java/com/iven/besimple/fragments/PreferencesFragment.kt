package com.iven.besimple.fragments

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.content.res.AppCompatResources
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import androidx.lifecycle.ViewModelProvider
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.iven.besimple.MusicViewModel
import com.iven.besimple.R
import com.iven.besimple.helpers.ThemeHelper
import com.iven.besimple.ui.UIControlInterface


class PreferencesFragment : PreferenceFragmentCompat(),
        SharedPreferences.OnSharedPreferenceChangeListener, Preference.OnPreferenceClickListener {

    private lateinit var mUIControlInterface: UIControlInterface

    private var mThemePreference: Preference? = null

    override fun setDivider(divider: Drawable?) {
        super.setDivider(null)
    }

    override fun onResume() {
        super.onResume()
        preferenceScreen.sharedPreferences.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onPause() {
        super.onPause()
        preferenceScreen.sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)

        // This makes sure that the container activity has implemented
        // the callback interface. If not, it throws an exception
        try {
            mUIControlInterface = activity as UIControlInterface
        } catch (e: ClassCastException) {
            e.printStackTrace()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        findPreference<Preference>(getString(R.string.open_git_pref))?.onPreferenceClickListener =
                this

        ViewModelProvider(requireActivity()).get(MusicViewModel::class.java).apply {
            deviceMusic.observe(viewLifecycleOwner, { returnedMusic ->
                if (!returnedMusic.isNullOrEmpty()) {
                    findPreference<Preference>(getString(R.string.found_songs_pref))?.let { preference ->
                        preference.title =
                                getString(R.string.found_songs_pref_title, musicDatabaseSize)
                    }
                }
            })
        }

        mThemePreference = findPreference<Preference>(getString(R.string.theme_pref))?.apply {
            icon = AppCompatResources.getDrawable(
                    requireActivity(),
                    ThemeHelper.resolveThemeIcon(requireActivity())
            )
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
    }

    override fun onPreferenceClick(preference: Preference?): Boolean {
        when (preference?.key) {
            getString(R.string.open_git_pref) -> openCustomTab(getString(R.string.app_git))
        }
        return false
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        when (key) {
            getString(R.string.theme_pref) -> {
                mThemePreference?.icon =
                        AppCompatResources.getDrawable(
                                requireActivity(),
                                ThemeHelper.resolveThemeIcon(requireActivity())
                        )
                mUIControlInterface.onThemeChanged()
            }
            getString(R.string.focus_pref) -> mUIControlInterface.onHandleFocusPref()
        }
    }

    @SuppressLint("QueryPermissionsNeeded")
    private fun openCustomTab(link: String) {
        val customTabsIntent = CustomTabsIntent.Builder()
                .setShareState(CustomTabsIntent.SHARE_STATE_ON)
                .setShowTitle(true)
                .build()

        val parsedUri = link.toUri()
        val manager = requireActivity().packageManager
        val infos = manager.queryIntentActivities(customTabsIntent.intent, 0)
        if (infos.size > 0) {
            customTabsIntent.launchUrl(requireActivity(), parsedUri)
        } else {

            //from: https://github.com/immuni-app/immuni-app-android/blob/development/extensions/src/main/java/it/ministerodellasalute/immuni/extensions/utils/ExternalLinksHelper.kt
            val browserIntent = Intent(Intent.ACTION_VIEW, parsedUri)
            browserIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK

            val fallbackInfos = manager.queryIntentActivities(browserIntent, 0)
            if (fallbackInfos.size > 0) {
                requireActivity().startActivity(browserIntent)
            } else {
                Toast.makeText(
                        requireActivity(),
                        requireActivity().getString(R.string.error_no_browser),
                        Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @return A new instance of fragment PreferencesFragment.
         */
        @JvmStatic
        fun newInstance() = PreferencesFragment()
    }
}
