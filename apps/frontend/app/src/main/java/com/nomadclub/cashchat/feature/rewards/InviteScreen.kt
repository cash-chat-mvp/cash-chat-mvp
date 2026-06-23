package com.nomadclub.cashchat.feature.rewards

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nomadclub.cashchat.shared.invite.InviteStatus
import com.nomadclub.cashchat.shared.invite.InviteStore
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

class InviteViewModel(private val store: InviteStore) : ViewModel() {
    val status: StateFlow<InviteStatus?> = store.status
    private val _toast = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val toast: SharedFlow<String> = _toast.asSharedFlow()
    var submitting by mutableStateOf(false)
        private set

    fun load() { viewModelScope.launch { runCatching { store.refresh() } } }

    fun redeem(code: String) {
        if (submitting) return
        viewModelScope.launch {
            submitting = true
            val result = runCatching { store.redeem(code) }.getOrNull()
            _toast.tryEmit(
                when {
                    result == null -> "잠시 후 다시 시도해주세요"
                    result.success -> "⚡${result.awardedEnergy} 에너지를 받았어요!"
                    else -> result.message ?: "코드를 확인해주세요"
                }
            )
            submitting = false
        }
    }
}

@Composable
fun InviteDialog(
    onDismiss: () -> Unit,
    vm: InviteViewModel = koinViewModel(),
) {
    val status by vm.status.collectAsState()
    val context = LocalContext.current
    var input by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { vm.load() }
    LaunchedEffect(vm) { vm.toast.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() } }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxWidth(0.92f).clip(RoundedCornerShape(24.dp)).background(Color(0xFFF6F5FA)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF7C6CFF), Color(0xFFFF5E8A)))).padding(18.dp),
            ) {
                Text("친구 초대하고 코인 받기 🎁", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                status?.let {
                    Text("친구가 가입하면 나는 🪙+${it.rewardCoin}, 친구는 ⚡+${it.rewardEnergy}!",
                        color = Color.White.copy(alpha = 0.92f), fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp))
                }
            }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).padding(15.dp)) {
                Text("내 추천 코드", fontSize = 12.sp, color = Color(0xFF6B6979))
                Text(status?.myCode ?: "-", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF1B1B2A),
                    modifier = Modifier.padding(top = 4.dp))
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        val code = status?.myCode ?: return@Button
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "캐시챗에서 만나요! 추천코드 [$code] 입력하면 에너지를 드려요 ⚡")
                        }
                        context.startActivity(Intent.createChooser(send, null))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("친구에게 공유하기") }
                status?.let {
                    Text("지금까지 ${it.invitedCount}명 초대", fontSize = 12.sp, color = Color(0xFF6B6979),
                        modifier = Modifier.padding(top = 9.dp))
                }
            }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).padding(15.dp)) {
                val available = status?.redeemAvailable ?: false
                Text(if (available) "추천 코드 입력" else "이미 추천 코드를 사용했어요",
                    fontSize = 12.sp, color = Color(0xFF6B6979))
                if (available) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = input, onValueChange = { input = it.uppercase() },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        placeholder = { Text("코드 입력") },
                    )
                    Spacer(Modifier.height(9.dp))
                    Button(onClick = { vm.redeem(input) }, enabled = !vm.submitting && input.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()) { Text("에너지 받기") }
                }
            }
            Text("닫기", color = Color(0xFF9A95AD), fontSize = 13.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally)
                    .clip(RoundedCornerShape(8.dp)).clickable { onDismiss() }.padding(8.dp))
        }
    }
}
