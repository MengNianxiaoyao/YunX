package com.yunx.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.SchemeTonalSpot

private val lightScheme = lightColorScheme(
    primary = primaryLight,
    onPrimary = onPrimaryLight,
    primaryContainer = primaryContainerLight,
    onPrimaryContainer = onPrimaryContainerLight,
    secondary = secondaryLight,
    onSecondary = onSecondaryLight,
    secondaryContainer = secondaryContainerLight,
    onSecondaryContainer = onSecondaryContainerLight,
    tertiary = tertiaryLight,
    onTertiary = onTertiaryLight,
    tertiaryContainer = tertiaryContainerLight,
    onTertiaryContainer = onTertiaryContainerLight,
    error = errorLight,
    onError = onErrorLight,
    errorContainer = errorContainerLight,
    onErrorContainer = onErrorContainerLight,
    background = backgroundLight,
    onBackground = onBackgroundLight,
    surface = surfaceLight,
    onSurface = onSurfaceLight,
    surfaceVariant = surfaceVariantLight,
    onSurfaceVariant = onSurfaceVariantLight,
    outline = outlineLight,
    outlineVariant = outlineVariantLight,
    scrim = scrimLight,
    inverseSurface = inverseSurfaceLight,
    inverseOnSurface = inverseOnSurfaceLight,
    inversePrimary = inversePrimaryLight,
    surfaceDim = surfaceDimLight,
    surfaceBright = surfaceBrightLight,
    surfaceContainerLowest = surfaceContainerLowestLight,
    surfaceContainerLow = surfaceContainerLowLight,
    surfaceContainer = surfaceContainerLight,
    surfaceContainerHigh = surfaceContainerHighLight,
    surfaceContainerHighest = surfaceContainerHighestLight,
)

private val darkScheme = darkColorScheme(
    primary = primaryDark,
    onPrimary = onPrimaryDark,
    primaryContainer = primaryContainerDark,
    onPrimaryContainer = onPrimaryContainerDark,
    secondary = secondaryDark,
    onSecondary = onSecondaryDark,
    secondaryContainer = secondaryContainerDark,
    onSecondaryContainer = onSecondaryContainerDark,
    tertiary = tertiaryDark,
    onTertiary = onTertiaryDark,
    tertiaryContainer = tertiaryContainerDark,
    onTertiaryContainer = onTertiaryContainerDark,
    error = errorDark,
    onError = onErrorDark,
    errorContainer = errorContainerDark,
    onErrorContainer = onErrorContainerDark,
    background = backgroundDark,
    onBackground = onBackgroundDark,
    surface = surfaceDark,
    onSurface = onSurfaceDark,
    surfaceVariant = surfaceVariantDark,
    onSurfaceVariant = onSurfaceVariantDark,
    outline = outlineDark,
    outlineVariant = outlineVariantDark,
    scrim = scrimDark,
    inverseSurface = inverseSurfaceDark,
    inverseOnSurface = inverseOnSurfaceDark,
    inversePrimary = inversePrimaryDark,
    surfaceDim = surfaceDimDark,
    surfaceBright = surfaceBrightDark,
    surfaceContainerLowest = surfaceContainerLowestDark,
    surfaceContainerLow = surfaceContainerLowDark,
    surfaceContainer = surfaceContainerDark,
    surfaceContainerHigh = surfaceContainerHighDark,
    surfaceContainerHighest = surfaceContainerHighestDark,
)

/**
 * 列表组外圈圆角：与下面 Shapes.large 共用同一个值，避免两处各写一份走样。
 */
private val CornerLarge = 16.dp

/**
 * M3 Expressive 圆角刻度。
 * 比经典 M3 多出 largeIncreased / extraLargeIncreased / extraExtraLarge 三档（20 / 32 / 48dp）：
 * Expressive 组件（工具栏、FAB 菜单、底部面板、按钮组…）默认取这三档，容器越大圆角越明显。
 * 本项目此前完全没定制过 Shapes，这里直接把整套刻度按 Expressive 规范钉死，避免各组件回落到经典刻度。
 */
private val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(CornerLarge),
    largeIncreased = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
    extraLargeIncreased = RoundedCornerShape(32.dp),
    extraExtraLarge = RoundedCornerShape(48.dp)
)

/** 列表组内的位置：决定这一项该用分段圆角里的哪一档 */
internal enum class ListGroupPos { FIRST, MIDDLE, LAST, SINGLE }

/** 组内衔接处的内圆角：刻意很小，相邻两项拼在一起时不会出现明显缺口 */
private val ListGroupInnerCorner = 2.dp

/**
 * 列表组内相邻两项之间的间距（发丝缝）。
 * 太小（1dp）会连成一片、看不出行与行的分界；太大就不像"一组"了 —— 想调组内间距只改这一处。
 */
internal val ListGroupGap = 3.dp

/**
 * 列表组分段圆角：让一组首尾相接的列表项看起来是「一整块」。
 *
 * 规则：首项只圆上两角、末项只圆下两角（都用 16dp 外圈圆角），中间项只留 2dp 内圆角。
 * 调用方需保证组内各项之间几乎没有间距（约定留 1dp 发丝缝）——留大间距的话
 * 中间项的小圆角会各自露出来，看着像一堆没对齐的卡片而不是一个列表组。
 */
internal fun listGroupShape(pos: ListGroupPos): RoundedCornerShape {
    val top = if (pos == ListGroupPos.FIRST || pos == ListGroupPos.SINGLE) CornerLarge else ListGroupInnerCorner
    val bottom = if (pos == ListGroupPos.LAST || pos == ListGroupPos.SINGLE) CornerLarge else ListGroupInnerCorner
    return RoundedCornerShape(topStart = top, topEnd = top, bottomEnd = bottom, bottomStart = bottom)
}

/** 按下标取列表组圆角：LazyColumn（itemsIndexed）等能拿到 index/count 的列表直接调用 */
internal fun listGroupShape(index: Int, count: Int): RoundedCornerShape = listGroupShape(
    when {
        count <= 1 -> ListGroupPos.SINGLE
        index <= 0 -> ListGroupPos.FIRST
        index >= count - 1 -> ListGroupPos.LAST
        else -> ListGroupPos.MIDDLE
    }
)

/**
 * 全局动效方案（弹簧物理：位移/尺寸用 spatial，透明度/颜色用 effects）。
 * 低端机掉帧或想更克制时，换成 MotionScheme.standard() 即可，这是全局唯一开关。
 *
 * ★ 刻意声明为 internal 顶层属性（而非私有）：`ui/theme/Motion.kt` 里的
 *   spatialDefault/effectsDefault 等顶层函数也用它，从而保证「组件内部动效」与
 *   「页面自定义动效」用的是同一个 scheme；只有一处可改。
 */
internal val AppMotionScheme = MotionScheme.expressive()

@Composable
fun ComposeEmptyActivityTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    // 主题设置由 ThemeController 内存状态驱动（首次从 SharedPreferences 加载）
    ThemeController.init(context)
    // 深色模式：0=跟随系统，1=浅色，2=深色
    val isDark = when (ThemeController.darkMode) {
        1 -> false
        2 -> true
        else -> darkTheme
    }
    val colorScheme = when {
        // 动态色彩：Android 12+ 从系统壁纸取色
        ThemeController.colorMode == 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        // 自定义种子色（预选色 / 颜色卡自选）：基于种子色生成完整 Material3 方案
        ThemeController.colorMode == 2 -> seedColorScheme(ThemeController.seedColor, isDark)
        // 默认蓝色（低版本动态色彩不可用时也回退到这里）
        isDark -> darkScheme
        else -> lightScheme
    }

    // Material 3 Expressive 主题入口：一次性注入 colorScheme / motionScheme / shapes / typography，
    // 所有 M3 组件据此切换到 Expressive 形态（尺寸、圆角、形变、弹簧动效），无需逐个组件改造。
    // 颜色部分完全沿用原有逻辑（动态取色 / 种子色 / 深浅色），Expressive 不改变颜色角色。
    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = AppMotionScheme,
        shapes = ExpressiveShapes,
        typography = Typography,
        content = content
    )
}

/**
 * 基于种子色生成完整 Material3 颜色方案（浅色/深色），
 * 使用 material-color-utilities 的 Tonal Spot 方案（与 Material You 同源算法）。
 */
@Composable
private fun seedColorScheme(seedArgb: Long, dark: Boolean): androidx.compose.material3.ColorScheme {
    val scheme = remember(seedArgb, dark) {
        SchemeTonalSpot(Hct.fromInt(seedArgb.toInt()), dark, 0.0)
    }
    return if (dark) darkColorScheme(
        primary = Color(scheme.primary),
        onPrimary = Color(scheme.onPrimary),
        primaryContainer = Color(scheme.primaryContainer),
        onPrimaryContainer = Color(scheme.onPrimaryContainer),
        secondary = Color(scheme.secondary),
        onSecondary = Color(scheme.onSecondary),
        secondaryContainer = Color(scheme.secondaryContainer),
        onSecondaryContainer = Color(scheme.onSecondaryContainer),
        tertiary = Color(scheme.tertiary),
        onTertiary = Color(scheme.onTertiary),
        tertiaryContainer = Color(scheme.tertiaryContainer),
        onTertiaryContainer = Color(scheme.onTertiaryContainer),
        error = Color(scheme.error),
        onError = Color(scheme.onError),
        errorContainer = Color(scheme.errorContainer),
        onErrorContainer = Color(scheme.onErrorContainer),
        background = Color(scheme.background),
        onBackground = Color(scheme.onBackground),
        surface = Color(scheme.surface),
        onSurface = Color(scheme.onSurface),
        surfaceVariant = Color(scheme.surfaceVariant),
        onSurfaceVariant = Color(scheme.onSurfaceVariant),
        outline = Color(scheme.outline),
        outlineVariant = Color(scheme.outlineVariant),
        scrim = Color(scheme.scrim),
        inverseSurface = Color(scheme.inverseSurface),
        inverseOnSurface = Color(scheme.inverseOnSurface),
        inversePrimary = Color(scheme.inversePrimary),
        surfaceDim = Color(scheme.surfaceDim),
        surfaceBright = Color(scheme.surfaceBright),
        surfaceContainerLowest = Color(scheme.surfaceContainerLowest),
        surfaceContainerLow = Color(scheme.surfaceContainerLow),
        surfaceContainer = Color(scheme.surfaceContainer),
        surfaceContainerHigh = Color(scheme.surfaceContainerHigh),
        surfaceContainerHighest = Color(scheme.surfaceContainerHighest),
    ) else lightColorScheme(
        primary = Color(scheme.primary),
        onPrimary = Color(scheme.onPrimary),
        primaryContainer = Color(scheme.primaryContainer),
        onPrimaryContainer = Color(scheme.onPrimaryContainer),
        secondary = Color(scheme.secondary),
        onSecondary = Color(scheme.onSecondary),
        secondaryContainer = Color(scheme.secondaryContainer),
        onSecondaryContainer = Color(scheme.onSecondaryContainer),
        tertiary = Color(scheme.tertiary),
        onTertiary = Color(scheme.onTertiary),
        tertiaryContainer = Color(scheme.tertiaryContainer),
        onTertiaryContainer = Color(scheme.onTertiaryContainer),
        error = Color(scheme.error),
        onError = Color(scheme.onError),
        errorContainer = Color(scheme.errorContainer),
        onErrorContainer = Color(scheme.onErrorContainer),
        background = Color(scheme.background),
        onBackground = Color(scheme.onBackground),
        surface = Color(scheme.surface),
        onSurface = Color(scheme.onSurface),
        surfaceVariant = Color(scheme.surfaceVariant),
        onSurfaceVariant = Color(scheme.onSurfaceVariant),
        outline = Color(scheme.outline),
        outlineVariant = Color(scheme.outlineVariant),
        scrim = Color(scheme.scrim),
        inverseSurface = Color(scheme.inverseSurface),
        inverseOnSurface = Color(scheme.inverseOnSurface),
        inversePrimary = Color(scheme.inversePrimary),
        surfaceDim = Color(scheme.surfaceDim),
        surfaceBright = Color(scheme.surfaceBright),
        surfaceContainerLowest = Color(scheme.surfaceContainerLowest),
        surfaceContainerLow = Color(scheme.surfaceContainerLow),
        surfaceContainer = Color(scheme.surfaceContainer),
        surfaceContainerHigh = Color(scheme.surfaceContainerHigh),
        surfaceContainerHighest = Color(scheme.surfaceContainerHighest),
    )
}
