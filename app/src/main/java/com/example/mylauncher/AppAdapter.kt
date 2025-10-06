package com.example.mylauncher

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AppAdapter(
    private val apps: MutableList<AppInfo>,
    private val onAppClick: (AppInfo) -> Unit,
    private val onMoveApp: (AppInfo) -> Unit
) : RecyclerView.Adapter<AppAdapter.AppViewHolder>() {

    class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val iconView: ImageView = itemView.findViewById(R.id.app_icon)
        val nameView: TextView = itemView.findViewById(R.id.app_name)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val app = apps[position]

        // фиксированный размер иконок (в dp → px)
        val density = holder.itemView.resources.displayMetrics.density
        val iconSizePx = (64 * density).toInt() // 🔹 64dp = одинаковые по размеру

        val drawable = app.appIcon
        drawable.setBounds(0, 0, iconSizePx, iconSizePx)
        holder.iconView.setImageDrawable(drawable)

        holder.nameView.text = app.appName

        holder.itemView.setOnClickListener { onAppClick(app) }
        holder.itemView.setOnLongClickListener {
            onMoveApp(app)
            true
        }
    }

    override fun getItemCount(): Int = apps.size
}
