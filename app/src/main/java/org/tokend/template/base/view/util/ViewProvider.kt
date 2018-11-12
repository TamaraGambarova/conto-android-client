package org.tokend.template.base.view.util

import android.view.View
import android.view.ViewGroup

interface ViewProvider {

    fun addTo(rootView: ViewGroup): ViewProvider
    fun getView(rootView: ViewGroup): View
}