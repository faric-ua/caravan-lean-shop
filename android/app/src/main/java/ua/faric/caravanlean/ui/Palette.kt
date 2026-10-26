package ua.faric.caravanlean.ui

/**
 * Кольори Caravan Lean — єдине місце, де вони задаються (перевіряє scripts/audit_ui.py).
 * Світла «магазинна» тема: теплий світлий фон, білі картки, акцент — лісовий зелений (кемпінг/караван).
 * Фірмові кольори сайту магазину підставляються сюди — і змінюється весь застосунок.
 * Вибране — рамка акцентом, без галочок (правило faric-ua).
 */
object Palette {
    const val bg = 0xFFF5F3EE.toInt()
    const val surface = 0xFFFFFFFF.toInt()
    const val surfaceAlt = 0xFFEFEBE4.toInt()
    const val border = 0xFFE3DED4.toInt()
    const val text = 0xFF1C2220.toInt()
    const val muted = 0xFF5F6964.toInt()
    const val faint = 0xFF9AA19D.toInt()
    const val accent = 0xFF2F6B4F.toInt()
    const val accentPressed = 0xFF24563F.toInt()
    const val onAccent = 0xFFFFFFFF.toInt()
    const val accentSoft = 0xFFE2EEE7.toInt()
    const val sale = 0xFFD5462C.toInt()
    const val saleSoft = 0xFFFBE6E1.toInt()
    const val inStock = 0xFF2C8A55.toInt()
    const val outOfStock = 0xFF9AA19D.toInt()
    const val warning = 0xFFB7791F.toInt()
    const val placeholder = 0xFFECE8E1.toInt()
    const val toastBg = 0xF01C2220.toInt()
    const val ripple = 0x1F000000
    const val transparent = 0x00000000
}
