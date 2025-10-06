package com.example.mylauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class PackageChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        // Здесь ничего не делаем.
        // Обновление списка ярлыков происходит в MainActivity.onResume()
    }
}
