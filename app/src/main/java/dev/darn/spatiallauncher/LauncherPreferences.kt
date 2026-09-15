package dev.darn.spatiallauncher

import android.content.Context
import android.content.ComponentName

class LauncherPreferences(context: Context) {
    private val store = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)
    var animation: Float
        get() = store.getFloat("animation", 0.82f)
        set(value) = store.edit().putFloat("animation", value).apply()
    var depth: Float
        get() = store.getFloat("depth", 0.88f)
        set(value) = store.edit().putFloat("depth", value).apply()
    var transition: Int
        get() = store.getInt("transition", 0)
        set(value) = store.edit().putInt("transition", value).apply()
    fun homeSlots(): MutableList<ComponentName?> = (0 until 18).map { i ->
        store.getString("slot_$i", null)?.let(ComponentName::unflattenFromString)
    }.toMutableList()
    fun saveSlots(slots: List<ComponentName?>) {
        store.edit().also { e -> slots.forEachIndexed { i, c -> if (c == null) e.remove("slot_$i") else e.putString("slot_$i", c.flattenToString()) } }.apply()
    }
}
