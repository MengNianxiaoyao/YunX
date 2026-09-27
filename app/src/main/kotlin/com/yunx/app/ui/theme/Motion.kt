package com.yunx.app.ui.theme

import androidx.compose.animation.core.FiniteAnimationSpec

/**
 * 全项目动效规格（Material 3 Expressive 弹簧）：位移/尺寸用 spatial，透明度/颜色用 effects。
 *
 * **为什么不直接写 `MaterialTheme.motionScheme`**：它是 `@Composable` 属性，只能出现在 composable 作用域里；
 * 而 `AnimatedContent` 的 `transitionSpec`、`remember {}` 内的动画等位置是**普通 lambda**，
 * 在那里读它会直接编译失败（`@Composable invocations can only happen from the context of a @Composable function`）。
 * `MotionScheme` 本身是普通对象、取规格的方法也不是 composable，因此这里做成顶层函数：
 * composable 与普通 lambda 都能调用，省掉每处都要「先 hoist 成局部变量」的样板。
 *
 * **单一来源**：[AppMotionScheme] 同时被主题（`Theme.kt` 的 `MaterialExpressiveTheme`）与本文件使用，
 * 所以组件内部动效与页面自定义动效永远同源；改一处即可全局切换风格（expressive ↔ standard）。
 *
 * **选型约定**：`spatial*` = 位置/尺寸/缩放/旋转；`effects*` = 透明度/颜色。
 * `*Default` = 进入与常规变化；`*Fast` = 退出与小范围变化。
 */

/** 移位/尺寸/缩放/旋转的默认规格（进入） */
fun <T> spatialDefault(): FiniteAnimationSpec<T> = AppMotionScheme.defaultSpatialSpec()

/** 移位/尺寸/缩放/旋转的快速规格（退出、小范围变化） */
fun <T> spatialFast(): FiniteAnimationSpec<T> = AppMotionScheme.fastSpatialSpec()

/** 透明度/颜色的默认规格（进入） */
fun <T> effectsDefault(): FiniteAnimationSpec<T> = AppMotionScheme.defaultEffectsSpec()

/** 透明度/颜色的快速规格（退出、小范围变化） */
fun <T> effectsFast(): FiniteAnimationSpec<T> = AppMotionScheme.fastEffectsSpec()
