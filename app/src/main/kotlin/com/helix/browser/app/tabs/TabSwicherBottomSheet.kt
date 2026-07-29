package com.helix.browser.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.floatingactionbutton.FloatingActionButton

class TabSwitcherBottomSheet : BottomSheetDialogFragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: TabSwitcherAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_tab_switcher, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.tabRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(context)

        val tabs = (activity as? DefaultActivity)?.getTabs() ?: emptyList()
        adapter = TabSwitcherAdapter(tabs) { position ->
            // Switch to tab
            (activity as? DefaultActivity)?.switchToTab(position)
            dismiss()
        } onClose = { position ->
            // Close tab
            (activity as? DefaultActivity)?.closeTab(position)
            // Refresh list
            updateTabs()
        }
        recyclerView.adapter = adapter

        // New tab button
        view.findViewById<FloatingActionButton>(R.id.newTabButton).setOnClickListener {
            (activity as? DefaultActivity)?.addNewTab()
            updateTabs()
            dismiss()
        }
    }

    fun updateTabs() {
        val tabs = (activity as? DefaultActivity)?.getTabs() ?: emptyList()
        adapter.updateTabs(tabs)
    }

    inner class TabSwitcherAdapter(
        private var tabs: List<TabFragment>,
        private val onItemClick: (Int) -> Unit,
        private val onClose: (Int) -> Unit
    ) : RecyclerView.Adapter<TabSwitcherAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_list_item_2, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val tab = tabs[position]
            val title = (tab.activity as? DefaultActivity)?.getTabTitle(tab) ?: "Tab ${position + 1}"
            holder.text1.text = title
            holder.text2.text = tab.url

            holder.itemView.setOnClickListener { onItemClick(position) }
            holder.itemView.setOnLongClickListener {
                onClose(position)
                true
            }
        }

        override fun getItemCount(): Int = tabs.size

        fun updateTabs(newTabs: List<TabFragment>) {
            tabs = newTabs
            notifyDataSetChanged()
        }

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val text1: TextView = itemView.findViewById(android.R.id.text1)
            val text2: TextView = itemView.findViewById(android.R.id.text2)
        }
    }
}
