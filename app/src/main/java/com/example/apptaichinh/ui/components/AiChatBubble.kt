package com.example.apptaichinh.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Bong bóng Trợ Lý AI nổi trên màn hình (Floating Chat Bubble / Chat Head).
 * - Biểu tượng Robot AI thân thiện (SmartToy) với gradient màu sắc hiện đại.
 * - Phần mô tả nhãn chữ "Trợ lý AI" ở ngay dưới để người dùng nhận diện ngay lập tức.
 * - Tự do kéo di chuyển (drag) khắp màn hình cực mượt.
 * - Tự động hít dính nam châm (magnetic snap) vào mép trái/phải gần nhất khi thả tay.
 * - Giới hạn an toàn trong phạm vi màn hình, không bị che khuất hay rơi ra ngoài.
 * - Chạm nhẹ (tap) để mở cửa sổ chat.
 */
@Composable
fun AiChatBubble(
    onClick: () -> Unit,
    isThinking: Boolean,
    modifier: Modifier = Modifier,
    containerWidth: Dp = 0.dp,
    containerHeight: Dp = 0.dp
) {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val coroutineScope = rememberCoroutineScope()
    val viewConfig = LocalViewConfiguration.current
    val touchSlop = viewConfig.touchSlop

    // Kích thước khả dụng của màn hình
    val effectiveWidthDp = if (containerWidth > 0.dp) containerWidth else configuration.screenWidthDp.dp
    val effectiveHeightDp = if (containerHeight > 0.dp) containerHeight else configuration.screenHeightDp.dp

    val widthPx = with(density) { effectiveWidthDp.toPx() }
    val heightPx = with(density) { effectiveHeightDp.toPx() }

    // Kích thước toàn bộ cụm bong bóng (bao gồm cả nhãn chữ bên dưới)
    val compWidthDp = 76.dp
    val compHeightDp = 86.dp
    val compWidthPx = with(density) { compWidthDp.toPx() }
    val compHeightPx = with(density) { compHeightDp.toPx() }
    val marginPx = with(density) { 10.dp.toPx() }

    val minX = marginPx
    val maxX = (widthPx - compWidthPx - marginPx).coerceAtLeast(minX)
    val minY = marginPx
    val maxY = (heightPx - compHeightPx - marginPx).coerceAtLeast(minY)

    // Trạng thái kéo
    var isDragging by remember { mutableStateOf(false) }

    // Vị trí mặc định ban đầu: góc dưới bên phải, cách đáy khoảng 92dp (trên thanh navigation)
    val initialX = remember(widthPx) { maxX }
    val initialY = remember(heightPx) {
        val bottomOffset = with(density) { 96.dp.toPx() }
        (heightPx - compHeightPx - bottomOffset).coerceIn(minY, maxY)
    }

    val animX = remember { Animatable(initialX) }
    val animY = remember { Animatable(initialY) }

    // Đồng bộ lại khi kích thước màn hình thay đổi
    LaunchedEffect(widthPx, heightPx) {
        if (animX.value > maxX) {
            animX.snapTo(maxX)
        }
        if (animY.value > maxY) {
            animY.snapTo(maxY)
        }
    }

    // Hiệu ứng nhịp thở ánh sáng khi ở trạng thái nghỉ
    val infiniteTransition = rememberInfiniteTransition(label = "bubblePulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    // Phóng to nhẹ và tăng độ nổi khi đang chạm giữ/kéo
    val dragScale by animateFloatAsState(
        targetValue = if (isDragging) 1.12f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "dragScale"
    )
    val elevation by animateDpAsState(
        targetValue = if (isDragging) 16.dp else 8.dp,
        label = "dragElevation"
    )

    Column(
        modifier = modifier
            .width(compWidthDp)
            .offset { IntOffset(animX.value.roundToInt(), animY.value.roundToInt()) }
            .scale(if (isDragging) dragScale else pulseScale)
            .pointerInput(widthPx, heightPx) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var totalDrag = Offset.Zero
                    var isDrag = false

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break

                        if (!change.pressed) {
                            // Nhấc ngón tay lên
                            if (!isDrag) {
                                onClick()
                            } else {
                                isDragging = false
                                // Hít nam châm (magnetic snap) vào mép gần nhất (trái hoặc phải)
                                val currentX = animX.value
                                val centerX = currentX + compWidthPx / 2f
                                val targetX = if (centerX < widthPx / 2f) minX else maxX
                                val targetY = animY.value.coerceIn(minY, maxY)

                                coroutineScope.launch {
                                    animX.animateTo(
                                        targetValue = targetX,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessLow
                                        )
                                    )
                                }
                                coroutineScope.launch {
                                    animY.animateTo(
                                        targetValue = targetY,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                            stiffness = Spring.StiffnessMedium
                                        )
                                    )
                                }
                            }
                            break
                        }

                        val dragDelta = change.positionChange()
                        totalDrag += dragDelta

                        if (!isDrag) {
                            if (totalDrag.getDistance() > touchSlop) {
                                isDrag = true
                                isDragging = true
                                change.consume()
                                coroutineScope.launch {
                                    animX.snapTo((animX.value + dragDelta.x).coerceIn(minX, maxX))
                                    animY.snapTo((animY.value + dragDelta.y).coerceIn(minY, maxY))
                                }
                            }
                        } else {
                            change.consume()
                            coroutineScope.launch {
                                animX.snapTo((animX.value + dragDelta.x).coerceIn(minX, maxX))
                                animY.snapTo((animY.value + dragDelta.y).coerceIn(minY, maxY))
                            }
                        }
                    }
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Cụm bong bóng hình tròn phía trên
        Box(
            modifier = Modifier.size(58.dp),
            contentAlignment = Alignment.Center
        ) {
            // Vòng hào quang tỏa sáng (Glow aura)
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF6366F1).copy(alpha = if (isDragging) 0.55f else 0.35f),
                                Color(0xFF06B6D4).copy(alpha = 0.2f),
                                Color.Transparent
                            )
                        )
                    )
            )

            // Khối nút tròn chính của Robot AI
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .shadow(
                        elevation = elevation,
                        shape = CircleShape,
                        ambientColor = Color(0xFF4F46E5),
                        spotColor = Color(0xFF06B6D4)
                    )
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF4338CA), // Deep Indigo
                                Color(0xFF6366F1), // Electric Purple/Blue
                                Color(0xFF06B6D4)  // Cyan
                            ),
                            start = Offset(0f, 0f),
                            end = Offset(120f, 120f)
                        )
                    )
                    .border(
                        width = 1.5.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.8f),
                                Color(0xFF38BDF8).copy(alpha = 0.5f)
                            )
                        ),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isThinking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        color = Color.White,
                        strokeWidth = 2.5.dp
                    )
                } else {
                    // Biểu tượng Robot AI thông minh
                    Icon(
                        imageVector = Icons.Default.SmartToy,
                        contentDescription = "Trợ Lý AI",
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }

            // Chấm đèn tín hiệu xanh lá "Online" ở góc trên bên phải
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-2).dp, y = 2.dp)
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF10B981))
                    .border(1.5.dp, Color.White, CircleShape)
            )
        }

        Spacer(modifier = Modifier.height(3.dp))

        // Phần nhãn mô tả chữ "Trợ lý AI" ở ngay dưới cho người dùng dễ nhận biết
        Box(
            modifier = Modifier
                .shadow(elevation = 4.dp, shape = RoundedCornerShape(10.dp))
                .clip(RoundedCornerShape(10.dp))
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF1E1B4B).copy(alpha = 0.95f), // Indigo đậm
                            Color(0xFF0F172A).copy(alpha = 0.98f)
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF818CF8).copy(alpha = 0.8f),
                            Color(0xFF38BDF8).copy(alpha = 0.6f)
                        )
                    ),
                    shape = RoundedCornerShape(10.dp)
                )
                .padding(horizontal = 7.dp, vertical = 2.5.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Trợ lý AI",
                color = Color.White,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.2.sp
            )
        }
    }
}
