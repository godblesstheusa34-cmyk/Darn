package dev.darn.spatiallauncher

object GridMath {
    fun index(x: Float, y: Float, width: Float, columns: Int, rowHeight: Float): Int? {
        if (x < 0 || y < 0 || x >= width || rowHeight <= 0 || columns <= 0) return null
        return (y / rowHeight).toInt() * columns + (x / (width / columns)).toInt()
    }
}
