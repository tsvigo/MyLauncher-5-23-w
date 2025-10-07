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
// 🔹 добавлено
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class MainActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var pageInfo: TextView
    private lateinit var navButton: Button
    private lateinit var searchButton: Button

    private val appsPages = mutableListOf<MutableList<AppInfo>>()
    private val allApps = mutableListOf<AppInfo>()  // для поиска
    private val appsPerPage = 24  // 🔹 24 ярлыка на страницу

    // 🔹 добавлено для сохранения
    private val prefs by lazy { getSharedPreferences("launcher_prefs", MODE_PRIVATE) }
    private val gson = Gson()

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
                // 🔹 теперь при долгом тапе открывается окно перемещения по номеру страницы
                showMoveAppDialog(app)
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

        // 🔹 восстановление сохранённого расположения
        val savedJson = prefs.getString("apps_layout", null)
        if (savedJson != null) {
            try {
                val type = object : TypeToken<List<List<String>>>() {}.type
                val savedLayout: List<List<String>> = gson.fromJson(savedJson, type)

                appsPages.clear()
                for (page in savedLayout) {
                    val pageList = mutableListOf<AppInfo>()
                    for (pkg in page) {
                        val found = apps.find { it.packageName == pkg }
                        if (found != null) pageList.add(found)
                    }
                    appsPages.add(pageList)
                }

                // если появились новые приложения — добавляем их в конец
                val savedPkgs = savedLayout.flatten().toSet()
                val newApps = apps.filter { it.packageName !in savedPkgs }
                if (newApps.isNotEmpty()) {
                    if (appsPages.isEmpty()) appsPages.add(mutableListOf())
                    appsPages.last().addAll(newApps)
                }

                return
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        appsPages.clear()
        apps.chunked(appsPerPage).forEach {
            appsPages.add(it.toMutableList())
        }
    }

    // 🔹 сохранение текущего расположения
    private fun saveAppsLayout() {
        val layout = appsPages.map { page -> page.map { it.packageName } }
        prefs.edit().putString("apps_layout", gson.toJson(layout)).apply()
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

    // 🔹 перемещение ярлыка по номеру страницы с ограничением +1
    private fun showMoveAppDialog(app: AppInfo) {
        val inflater = LayoutInflater.from(this)
        val dialogView = inflater.inflate(R.layout.dialog_page_navigation, null)
        val input = dialogView.findViewById<EditText>(R.id.page_number_input)
        val goButton = dialogView.findViewById<Button>(R.id.go_button)

        val dialog = AlertDialog.Builder(this, R.style.AppTheme_Dialog)
            .setTitle("Переместить «${app.appName}»")
            .setView(dialogView)
            .create()

        goButton.text = "Переместить"

        goButton.setOnClickListener {
            val targetPage = input.text.toString().toIntOrNull()
            val currentPage = viewPager.currentItem
            val totalPages = appsPages.size

            if (targetPage == null || targetPage < 1) {
                input.error = "Введите число от 1"
                return@setOnClickListener
            }

            when {
                // 🔹 Перемещение в существующую страницу
                targetPage <= totalPages -> {
                    moveAppToPage(app, currentPage, targetPage)
                }

                // 🔹 Добавляем ровно одну новую страницу
                targetPage == totalPages + 1 -> {
                    appsPages.add(mutableListOf())
                    moveAppToPage(app, currentPage, targetPage)
                }

                // 🔹 Если пытаются добавить дальше чем +1 — ошибка
                else -> {
                    input.error = "Можно добавить только страницу ${totalPages + 1}"
                    return@setOnClickListener
                }
            }

            dialog.dismiss()
        }

        dialog.show()
    }

    // 🔹 вспомогательная функция переноса и сохранения
    private fun moveAppToPage(app: AppInfo, fromPage: Int, toPage: Int) {
        if (appsPages[fromPage].remove(app)) {
            appsPages[toPage - 1].add(app)
            viewPager.adapter?.notifyDataSetChanged()
            saveAppsLayout()
            Toast.makeText(
                this,
                "«${app.appName}» перемещено на страницу $toPage",
                Toast.LENGTH_SHORT
            ).show()
            viewPager.currentItem = toPage - 1
        } else {
            Toast.makeText(this, "Ошибка перемещения", Toast.LENGTH_SHORT).show()
        }
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
