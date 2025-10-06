package com.example.mylauncher

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ListView
import android.text.TextWatcher

import android.text.Editable
import android.widget.Toast
//----------------------------
import android.widget.AutoCompleteTextView
import android.widget.ArrayAdapter
import android.graphics.Color
//-------------
import android.widget.Filter
import android.widget.Filterable


import android.widget.Filter.FilterResults



class MainActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var pageInfo: TextView
    private lateinit var navButton: Button
    private lateinit var searchButton: Button

    private val appsPages = mutableListOf<MutableList<AppInfo>>()
    private val allApps = mutableListOf<AppInfo>()  // для поиска
    private val appsPerPage = 28  // 🔹 28 ярлыков на страницу

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        viewPager = findViewById(R.id.view_pager)
        pageInfo = findViewById(R.id.page_info)
        navButton = findViewById(R.id.nav_button)
        searchButton = findViewById(R.id.search_button)

        loadApps()

        val adapter = PageAdapter(
            pages = appsPages,
            onAppClick = { app ->
                val launchIntent = packageManager.getLaunchIntentForPackage(app.packageName)
                launchIntent?.let { startActivity(it) }
            },
            onMoveApp = { app ->
                println("Long pressed: ${app.appName}")
            }
        )

        viewPager.adapter = adapter
        updatePageInfo(0)

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updatePageInfo(position)
            }
        })

        navButton.setOnClickListener { showNavigationDialog() }
        searchButton.setOnClickListener { showSearchDialog() }
    }

    private fun loadApps() {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolveInfos = pm.queryIntentActivities(intent, 0)

        val apps = resolveInfos.map {
            AppInfo(
                appName = it.loadLabel(pm).toString(),
                appIcon = it.loadIcon(pm),
                packageName = it.activityInfo.packageName
            )
        }.sortedBy { it.appName }

        allApps.clear()
        allApps.addAll(apps)

        appsPages.clear()
        apps.chunked(appsPerPage).forEach {
            appsPages.add(it.toMutableList())
        }
    }

    private fun updatePageInfo(currentPage: Int) {
        val totalPages = appsPages.size.coerceAtLeast(1)
        pageInfo.text = "страница ${currentPage + 1} из $totalPages"
    }

    private fun showNavigationDialog() {
        val inflater = LayoutInflater.from(this)
        val dialogView = inflater.inflate(R.layout.dialog_page_navigation, null)
        val input = dialogView.findViewById<EditText>(R.id.page_number_input)
        val goButton = dialogView.findViewById<Button>(R.id.go_button)
        val dialog = AlertDialog.Builder(this, R.style.AppTheme_Dialog)
            .setView(dialogView)
            .create()

        goButton.setOnClickListener {
            val page = input.text.toString().toIntOrNull()
            if (page != null && page in 1..appsPages.size) {
                viewPager.currentItem = page - 1
                dialog.dismiss()
            } else {
                input.error = "Введите число от 1 до ${appsPages.size}"
            }
        }

        dialog.show()
    }

    private fun showSearchDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_search, null)
        val searchInput = dialogView.findViewById<AutoCompleteTextView>(R.id.search_input)

        val dialog = AlertDialog.Builder(this)
            .setTitle("Поиск приложения")
            .setView(dialogView)
            .create()

        val adapter = object : ArrayAdapter<AppInfo>(
            this,
            android.R.layout.simple_dropdown_item_1line,
            allApps.toMutableList()
        ), Filterable {

            private val originalList = ArrayList(allApps)

            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = convertView ?: layoutInflater.inflate(android.R.layout.simple_list_item_1, parent, false)
                val textView = view.findViewById<TextView>(android.R.id.text1)
                val app = getItem(position)
                textView.text = app?.appName ?: ""
                textView.setTextColor(Color.WHITE)

                // Добавляем иконку слева
                app?.appIcon?.setBounds(0, 0, 100, 100)
                textView.setCompoundDrawables(app?.appIcon, null, null, null)
                textView.compoundDrawablePadding = 16

                return view
            }

            override fun getFilter(): Filter {
                return object : Filter() {
                    override fun performFiltering(constraint: CharSequence?): FilterResults {
                        val results = FilterResults()
                        val filteredList = if (constraint.isNullOrBlank()) {
                            originalList
                        } else {
                            originalList.filter {
                                it.appName.contains(constraint, ignoreCase = true)
                            }
                        }
                        results.values = filteredList
                        results.count = filteredList.size
                        return results
                    }

                    override fun publishResults(constraint: CharSequence?, results: FilterResults) {
                        clear()
                        @Suppress("UNCHECKED_CAST")
                        addAll(results.values as List<AppInfo>)
                        notifyDataSetChanged()
                    }
                }
            }
        }

        searchInput.setAdapter(adapter)
        searchInput.threshold = 1
        searchInput.setDropDownBackgroundResource(android.R.color.background_dark)
        searchInput.requestFocus()

        // Сбрасываем фильтр при каждом открытии
        searchInput.text = null
        adapter.clear()
        adapter.addAll(allApps)

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                adapter.filter.filter(s)
                searchInput.showDropDown()
            }
        })

        searchInput.setOnItemClickListener { _, _, position, _ ->
            val app = adapter.getItem(position)
            if (app != null) {
                val launchIntent = packageManager.getLaunchIntentForPackage(app.packageName)
                if (launchIntent != null) startActivity(launchIntent)
                else Toast.makeText(this, "Не удалось открыть приложение", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }

        dialog.show()
    }







}
