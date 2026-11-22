package com.algermusic.player.ui.components

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.algermusic.player.ui.theme.EmeraldAccent
import com.algermusic.player.ui.viewmodel.AuthUiState
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.kyant.shapes.RoundedRectangle

@Composable
fun LoginDialog(
    authState: AuthUiState,
    onDismiss: () -> Unit,
    onOpenWebLogin: () -> Unit,
    onStartQr: () -> Unit,
    onCancelQr: () -> Unit,
    onLoginUid: (String) -> Unit,
    onLoginCookie: (String) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var uidInput by remember { mutableStateOf("") }
    var cookieInput by remember { mutableStateOf("") }

    LaunchedEffect(selectedTab) {
        if (selectedTab == 1 && authState.qrUrl == null && !authState.isLoggingIn) {
            onStartQr()
        } else if (selectedTab != 1) {
            onCancelQr()
        }
    }

    LaunchedEffect(authState.userProfile) {
        if (authState.userProfile != null) {
            onDismiss()
        }
    }

    AlertDialog(
        onDismissRequest = {
            onCancelQr()
            onDismiss()
        },
        shape = RoundedRectangle(24.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        title = {
            Text(
                text = "登录网易云音乐",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AlgerSegmentedTabs(
                    tabs = listOf("手机号", "扫码", "UID", "Cookie"),
                    selectedIndex = selectedTab,
                    onSelect = { selectedTab = it }
                )

                when (selectedTab) {
                    0 -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(EmeraldAccent.copy(alpha = 0.08f))
                                    .border(
                                        0.6.dp,
                                        EmeraldAccent.copy(alpha = 0.25f),
                                        RoundedCornerShape(16.dp)
                                    )
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "官方安全登录页（免扫码）",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "支持手机号短信验证码登录、手机号/邮箱密码登录，页内可直接完成滑块安全验证并自动同步黑胶 VIP 与个人歌单。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Button(
                                onClick = {
                                    onCancelQr()
                                    onDismiss()
                                    onOpenWebLogin()
                                },
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldAccent)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PhoneAndroid,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("手机验证码 / 账密登录", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    1 -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            val qrBitmap = remember(authState.qrUrl) {
                                authState.qrUrl?.let { generateQrBitmap(it, 480) }
                            }
                            if (qrBitmap != null) {
                                Box(
                                    modifier = Modifier
                                        .size(184.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Color.White)
                                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                                        .padding(10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        bitmap = qrBitmap.asImageBitmap(),
                                        contentDescription = "登录二维码",
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier.size(184.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(color = EmeraldAccent)
                                }
                            }
                            Text(
                                text = authState.qrStatusText.ifBlank { "准备二维码中..." },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedButton(
                                onClick = onStartQr,
                                shape = RoundedCornerShape(18.dp)
                            ) {
                                Text("刷新二维码")
                            }
                        }
                    }

                    2 -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "输入网易云数字 UID（免密同步个人公开歌单）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedTextField(
                                value = uidInput,
                                onValueChange = { uidInput = it },
                                label = { Text("网易云 UID") },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Button(
                                onClick = { onLoginUid(uidInput) },
                                enabled = !authState.isLoggingIn && uidInput.isNotBlank(),
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldAccent)
                            ) {
                                Text("确认绑定 UID", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    3 -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "粘贴包含 MUSIC_U=... 的完整 Cookie（支持黑胶 VIP 与每日推荐）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedTextField(
                                value = cookieInput,
                                onValueChange = { cookieInput = it },
                                label = { Text("MUSIC_U=...; __csrf=...") },
                                minLines = 3,
                                maxLines = 5,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Button(
                                onClick = { onLoginCookie(cookieInput) },
                                enabled = !authState.isLoggingIn && cookieInput.isNotBlank(),
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldAccent)
                            ) {
                                Text("验证并登录", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                authState.loginError?.let { err ->
                    Text(
                        text = err,
                        color = Color(0xFFEF4444),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onCancelQr()
                    onDismiss()
                }
            ) {
                Text("关闭")
            }
        }
    )
}

private fun generateQrBitmap(content: String, sizePx: Int): Bitmap? {
    return runCatching {
        val hints = mapOf(EncodeHintType.MARGIN to 1)
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565)
        for (x in 0 until sizePx) {
            for (y in 0 until sizePx) {
                bmp.setPixel(x, y, if (matrix[x, y]) AndroidColor.BLACK else AndroidColor.WHITE)
            }
        }
        bmp
    }.getOrNull()
}
