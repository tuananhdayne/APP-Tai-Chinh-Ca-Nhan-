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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
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
 * - Tự do kéo di chuyển (drag) khắp màn hình cực mượt.
 * - Tự động hít dính nam châm (magnetic snap) vào mép trái/phải gần nhất khi thả tay.
 * - Giới hạn an toàn trong phạm vi màn hình, không bao giờ bị rơi ra ngoài.
 * - Chạm nhẹ (tap) để mở cửa sổ chat.
 * - Hiệu ứng hào quang phát sáng và phóng to khi chạm kéo.
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

    val bubbleSize = 64.dp
    val bubbleSizePx = with(density) { bubbleSize.toPx() }
    val marginPx = with(density) { 16.dp.toPx() }

    val minX = marginPx
    val maxX = (widthPx - bubbleSizePx - marginPx).coerceAtLeast(minX)
    val minY = marginPx
    val maxY = (heightPx - bubbleSizePx - marginPx).coerceAtLeast(minY)

    // Trạng thái kéo
    var isDragging by remember { mutableStateOf(false) }

    // Vị trí mặc định ban đầu: góc dưới bên phải, cách đáy khoảng 88dp (trên thanh nav & fab)
    val initialX = remember(widthPx) { maxX }
    val initialY = remember(heightPx) {
        val bottomOffset = with(density) { 88.dp.toPx() }
        (heightPx - bubbleSizePx - bottomOffset).coerceIn(minY, maxY)
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

    // Hiệu ứng nhịp đập ánh sáng khi ở trạng thái nghỉ
    val infiniteTransition = rememberInfiniteTransition(label = "bubblePulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    // Độ phóng to và đổ bóng khi đang được ngón tay giữ kéo
    val dragScale by animateFloatAsState(
        targetValue = if (isDragging) 1.15f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "dragScale"
    )
    val elevation by animateDpAsState(
        targetValue = if (isDragging) 16.dp else 8.dp,
        label = "dragElevation"
    )

    Box(
        modifier = modifier
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
                                val centerX = currentX + bubbleSizePx / 2f
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
        contentAlignment = Alignment.Center
    ) {
        // Vòng hào quang phát sáng phía sau
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF8B5CF6).copy(alpha = if (isDragging) 0.6f else 0.4f),
                            Color(0xFFEC4899).copy(alpha = 0.15f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Khối bong bóng chính
        Box(
            modifier = Modifier
                .size(56.dp)
                .shadow(
                    elevation = elevation,
                    shape = CircleShape,
                    ambientColor = Color(0xFF6366F1),
                    spotColor = Color(0xFFEC4899)
                )
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF4F46E5), // Indigo
                            Color(0xFF7C3AED), // Purple
                            Color(0xFFDB2777)  // Pink
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(100f, 100f)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isThinking) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    color = Color.White,
                    strokeWidth = 2.5.dp
                )
            } else {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "Trợ Lý AI",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }
        }

        // Huy hiệu "AI" nhỏ ở góc trên bên phải
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 2.dp, y = (-2).dp)
                .clip(CircleShape)
                .background(Color(0xFF10B981))
                .padding(horizontal = 5.dp, vertical = 1.dp)
        ) {
            Text(
                text = "AI",
                color = Color.White,
                fontSize = 9.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}
