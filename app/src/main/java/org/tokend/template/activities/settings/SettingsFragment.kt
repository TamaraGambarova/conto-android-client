package org.tokend.template.activities.settings

import android.content.SharedPreferences
import android.os.Bundle
import android.support.v7.preference.ListPreference
import android.support.v7.preference.PreferenceFragmentCompat
import android.view.View
import org.tokend.template.R

abstract class SettingsFragment : PreferenceFragmentCompat(),
        SharedPreferences.OnSharedPreferenceChangeListener {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        reloadPreferences()
    }

    protected open fun reloadPreferences() {
        preferenceScreen = null
        setPreferencesFromResource(R.xml.preferences, getScreenKey())

        preferenceScreen.shouldDisableView = true
    }

    protected abstract fun getScreenKey(): String?

    override fun onResume() {
        super.onResume()
        preferenceManager.sharedPreferences
                .registerOnSharedPreferenceChangeListener(this)
    }

    override fun onPause() {
        super.onPause()
        preferenceManager.sharedPreferences
                .unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        listView.addItemDecoration(
                PreferenceDividerDecoration(context,
                        R.drawable.line_divider, R.dimen.divider_height)
                        .setPaddingLeft(resources
                                .getDimensionPixelSize(R.dimen.divider_with_icon_padding_left))
                        .drawBetweenItems(true)
                        .drawTop(false)
                        .drawBottom(false)
                        .drawBetweenCategories(false))
    }

    protected fun updateSummary(key: String, value: String) {
        val preference = findPreference(key)
        if (preference != null) {
            preference.summary = value
        }
    }

    protected fun updateSummary(key: String) {
        val preference = findPreference(key)
        if (preference != null && preference is ListPreference) {
            val entry = preference.entry
            if (entry != null) {
                updateSummary(key, entry.toString())
            }
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        updateSummary(key)
    }
}