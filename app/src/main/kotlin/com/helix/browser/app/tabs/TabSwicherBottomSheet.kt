package com.helix.browser.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class TabSwitcherBottomSheet : BottomSheetDialogFragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: TabSwitcherAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }
        val title = TextView(requireContext()).apply {
            text = "Open Tabs"
            textSize = 20f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        root.addView(title)

        recyclerView = RecyclerView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        }
        root.addView(recyclerView)

        val addBtn = Button(requireContext()).apply {
            text = "+ New Tab"
            setOnClickListener {
                (activity as? DefaultActivity)?.addNewTab()
                dismiss()
            }
        }
        root.addView(addBtn)
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        val tabs = (activity as? DefaultActivity)?.getTabs() ?: emptyList()
        adapter = TabSwitcherAdapter(tabs) { position ->
            (activity as? DefaultActivity)?.switchToTab(position)
            dismiss()
        } onClose = { position ->
            (activity as? DefaultActivity)?.closeTab(position)
            (activity as? DefaultActivity)?.let { adapter.updateTabs(it.getTabs()) }
        }
        recyclerView.adapter = adapter
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
            val tv = TextView(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                setPadding(16, 16, 16, 16)
                textSize = 16f
            }
            return ViewHolder(tv)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val tab = tabs[position]
            val title = (tab.activity as? DefaultActivity)?.getTabTitle(tab) ?: "Tab ${position+1}"
            holder.textView.text = title
            holder.textView.setOnClickListener { onItemClick(position) }
            holder.textView.setOnLongClickListener { onClose(position); true }
        }

        override fun getItemCount() = tabs.size
        fun updateTabs(newTabs: List<TabFragment>) { tabs = newTabs; notifyDataSetChanged() }

        inner class ViewHolder(val textView: TextView) : RecyclerView.ViewHolder(textView)
    }
}
