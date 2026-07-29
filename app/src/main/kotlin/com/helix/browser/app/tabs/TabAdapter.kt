package com.helix.browser.app

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class TabAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    private val fragments = mutableListOf<TabFragment>()

    override fun getItemCount(): Int = fragments.size

    override fun createFragment(position: Int): Fragment = fragments[position]

    fun addTab(fragment: TabFragment) {
        fragments.add(fragment)
        notifyItemInserted(fragments.size - 1)
    }

    fun removeTab(position: Int) {
        fragments.removeAt(position)
        notifyItemRemoved(position)
        // Also destroy the fragment's WebView to free memory
        // The adapter automatically handles fragment destruction
    }

    fun getTab(position: Int): TabFragment = fragments[position]

    fun getTabCount() = fragments.size
}
