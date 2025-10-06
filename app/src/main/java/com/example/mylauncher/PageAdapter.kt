package com.example.mylauncher

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

class PageAdapter(
    private val pages: List<MutableList<AppInfo>>,
    private val onAppClick: (AppInfo) -> Unit,
    private val onMoveApp: (AppInfo) -> Unit
) : RecyclerView.Adapter<PageAdapter.PageViewHolder>() {

    class PageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val recyclerView: RecyclerView = itemView.findViewById(R.id.page_recycler_view)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_page, parent, false)
        return PageViewHolder(view)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        val appsOnPage = pages[position]
        holder.recyclerView.layoutManager = GridLayoutManager(holder.itemView.context, 4)
        holder.recyclerView.adapter = AppAdapter(appsOnPage, onAppClick, onMoveApp)
    }

    override fun getItemCount(): Int = pages.size
}
