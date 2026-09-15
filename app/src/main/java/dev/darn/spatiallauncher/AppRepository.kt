package dev.darn.spatiallauncher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

class AppRepository(private val context: Context) {
    fun load(): List<AppEntry> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .filter { it.activityInfo.packageName != context.packageName }
            .map { AppEntry(it.loadLabel(context.packageManager).toString(), it.activityInfo.let { a -> android.content.ComponentName(a.packageName, a.name) }, it.loadIcon(context.packageManager)) }
            .sortedBy { it.label.lowercase() }
    }

    fun launch(app: AppEntry) {
        context.startActivity(Intent.makeMainActivity(app.component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
