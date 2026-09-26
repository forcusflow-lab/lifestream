package com.forcusflow.lifestream.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path

@Composable
fun ThemeWallpaper(
    wallpaperType: WallpaperType,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        when (wallpaperType) {
            WallpaperType.MISTY_FOREST -> MistyForestWallpaper()
            WallpaperType.PARIS_DUSK -> ParisDuskWallpaper()
            WallpaperType.TOKYO_NIGHT -> TokyoNightWallpaper()
        }
    }
}

/**
 * アルプスの大自然・朝霧の針葉樹林
 * 高解像度グラデーションメッシュ ＋ 針葉樹シルエット ＋ 柔らかな大気の霧
 */
@Composable
private fun MistyForestWallpaper() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // 1. 大気のベースグラデーション（冷涼な朝の霧空から深緑の大地へ）
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFFE2EBE5), // 淡い霧空
                    Color(0xFFC7DBD0), // 朝霧の中層
                    Color(0xFF8CAFA0), // 遠景の針葉樹霞
                    Color(0xFF426856), // 中景の深い緑
                    Color(0xFF1E3A2D)  // 地表の深緑
                ),
                startY = 0f,
                endY = h
            )
        )

        // 2. 遠くの山並み・霞む尾根（第1層）
        val ridge1 = Path().apply {
            moveTo(0f, h * 0.42f)
            cubicTo(w * 0.25f, h * 0.38f, w * 0.45f, h * 0.45f, w * 0.7f, h * 0.40f)
            cubicTo(w * 0.85f, h * 0.37f, w * 0.95f, h * 0.43f, w, h * 0.41f)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(ridge1, color = Color(0x33426856))

        // 3. 柔らかな山霧の光彩（ボケ）
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x55FFFFFF), Color(0x00FFFFFF)),
                center = Offset(w * 0.3f, h * 0.45f),
                radius = w * 0.5f
            ),
            radius = w * 0.5f,
            center = Offset(w * 0.3f, h * 0.45f)
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x44D9EBE1), Color(0x00D9EBE1)),
                center = Offset(w * 0.75f, h * 0.6f),
                radius = w * 0.6f
            ),
            radius = w * 0.6f,
            center = Offset(w * 0.75f, h * 0.6f)
        )

        // 4. 中景の針葉樹の尾根（第2層）
        val ridge2 = Path().apply {
            moveTo(0f, h * 0.62f)
            cubicTo(w * 0.3f, h * 0.58f, w * 0.55f, h * 0.66f, w * 0.8f, h * 0.61f)
            cubicTo(w * 0.9f, h * 0.59f, w * 0.98f, h * 0.63f, w, h * 0.62f)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(ridge2, color = Color(0x442C4C3B))

        // 5. 可読性を担保するナチュラルなすりガラスベール
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0x33F0FDF4),
                    Color(0x55F0FDF4),
                    Color(0x77E6F4EA)
                )
            )
        )
    }
}

/**
 * 黄昏のヨーロッパ街並み（パリ・トワイライト）
 * 夕暮れのセピア・パープル空 ＋ 温かな街灯ボケ ＋ 古典建築のスカイライン
 */
@Composable
private fun ParisDuskWallpaper() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // 1. トワイライトの夕空グラデーション（逢魔が時のセピア・パープルから黄金色へ）
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF281834), // 深い宵の紫
                    Color(0xFF552D4D), // トワイライトマゼンタ
                    Color(0xFF8B3E45), // 夕焼けのテラコッタ
                    Color(0xFFD97736), // 黄金の残光
                    Color(0xFFFBBF24), // 地平線の黄昏
                    Color(0xFF382318)  // 石畳の街の影
                ),
                startY = 0f,
                endY = h
            )
        )

        // 2. 温もりある街灯・カフェの光の玉ボケ（Bokeh）
        val bokehList = listOf(
            Triple(Offset(w * 0.22f, h * 0.38f), w * 0.28f, Color(0x35FDE68A)),
            Triple(Offset(w * 0.78f, h * 0.32f), w * 0.35f, Color(0x2EF59E0B)),
            Triple(Offset(w * 0.85f, h * 0.68f), w * 0.22f, Color(0x38F97316)),
            Triple(Offset(w * 0.15f, h * 0.72f), w * 0.26f, Color(0x30FBBF24)),
            Triple(Offset(w * 0.50f, h * 0.52f), w * 0.40f, Color(0x25FDE68A))
        )
        bokehList.forEach { (center, radius, color) ->
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color, color.copy(alpha = 0f)),
                    center = center,
                    radius = radius
                ),
                radius = radius,
                center = center
            )
        }

        // 3. ヨーロッパ古典建築（マンサード屋根や尖塔）のシルエットスカイライン
        val skyline = Path().apply {
            moveTo(0f, h * 0.74f)
            lineTo(w * 0.12f, h * 0.74f)
            lineTo(w * 0.16f, h * 0.69f) // 屋根の傾斜
            lineTo(w * 0.24f, h * 0.69f)
            lineTo(w * 0.28f, h * 0.73f)
            lineTo(w * 0.42f, h * 0.73f)
            lineTo(w * 0.45f, h * 0.65f) // 尖塔
            lineTo(w * 0.48f, h * 0.73f)
            lineTo(w * 0.68f, h * 0.73f)
            lineTo(w * 0.72f, h * 0.68f) // マンサード屋根
            lineTo(w * 0.82f, h * 0.68f)
            lineTo(w * 0.86f, h * 0.74f)
            lineTo(w, h * 0.74f)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(skyline, color = Color(0x401E110A))

        // 4. 文字可読性を高める温かなアンバーベール
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0x30281834),
                    Color(0x40FFFBEB),
                    Color(0x60FEF3C7)
                )
            )
        )
    }
}

/**
 * 煌めく夜景・メトロポリス（トーキョー・サイバーナイト）
 * 深夜の摩天楼ネイビー ＋ ネオンサイバーボケ（シアン・マゼンタ・ゴールド）
 */
@Composable
private fun TokyoNightWallpaper() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // 1. 深夜の都会グラデーション
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF04060E),
                    Color(0xFF0B1020),
                    Color(0xFF0F1A34),
                    Color(0xFF142445),
                    Color(0xFF0A0F1D)
                ),
                startY = 0f,
                endY = h
            )
        )

        // 2. 摩天楼の光彩・ネオンボケ
        val neonList = listOf(
            Triple(Offset(w * 0.25f, h * 0.28f), w * 0.35f, Color(0x2838BDF8)), // シアン
            Triple(Offset(w * 0.82f, h * 0.42f), w * 0.40f, Color(0x22EC4899)), // マゼンタ
            Triple(Offset(w * 0.50f, h * 0.75f), w * 0.32f, Color(0x25F59E0B)), // アンバー光
            Triple(Offset(w * 0.15f, h * 0.65f), w * 0.28f, Color(0x266366F1)), // インディゴ
            Triple(Offset(w * 0.88f, h * 0.82f), w * 0.30f, Color(0x2210B981))  // エメラルド
        )
        neonList.forEach { (center, radius, color) ->
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color, color.copy(alpha = 0f)),
                    center = center,
                    radius = radius
                ),
                radius = radius,
                center = center
            )
        }

        // 3. 高層ビルの幾何学グリッド・光のシルエット
        val buildings = Path().apply {
            moveTo(0f, h * 0.78f)
            lineTo(w * 0.18f, h * 0.78f)
            lineTo(w * 0.18f, h * 0.66f) // タワー1
            lineTo(w * 0.32f, h * 0.66f)
            lineTo(w * 0.32f, h * 0.74f)
            lineTo(w * 0.48f, h * 0.74f)
            lineTo(w * 0.48f, h * 0.58f) // メインタワー
            lineTo(w * 0.62f, h * 0.58f)
            lineTo(w * 0.62f, h * 0.72f)
            lineTo(w * 0.78f, h * 0.72f)
            lineTo(w * 0.78f, h * 0.64f) // タワー3
            lineTo(w * 0.90f, h * 0.64f)
            lineTo(w * 0.90f, h * 0.80f)
            lineTo(w, h * 0.80f)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(buildings, color = Color(0x35080E1C))

        // 4. 文字可読性を100%保持するダークスレートベール
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0x330F172A),
                    Color(0x550F172A),
                    Color(0x880B1220)
                )
            )
        )
    }
}
