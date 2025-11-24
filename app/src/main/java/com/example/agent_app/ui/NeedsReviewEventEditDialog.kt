package com.example.agent_app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.agent_app.data.entity.Event
import com.example.agent_app.ui.theme.Dimens
import java.util.Calendar
import java.util.TimeZone

/**
 * MOA-Needs-Review: NeedsReviewScreen용 EventEditDialog
 * MainScreen의 EventEditDialog와 동일한 기능 제공
 */
@Composable
fun EventEditDialog(
    event: Event,
    onDismiss: () -> Unit,
    onSave: (Event) -> Unit
) {
    var title by remember { mutableStateOf(event.title) }
    var location by remember { mutableStateOf(event.location ?: "") }
    var body by remember { mutableStateOf(event.body ?: "") }
    
    // 시작 시간 파싱
    val now = Calendar.getInstance(TimeZone.getTimeZone("Asia/Seoul"))
    
    var startYear by remember { 
        mutableStateOf(
            if (event.startAt != null) {
                Calendar.getInstance(TimeZone.getTimeZone("Asia/Seoul")).apply {
                    timeInMillis = event.startAt
                }.get(Calendar.YEAR)
            } else {
                now.get(Calendar.YEAR)
            }
        )
    }
    var startMonth by remember { 
        mutableStateOf(
            if (event.startAt != null) {
                Calendar.getInstance(TimeZone.getTimeZone("Asia/Seoul")).apply {
                    timeInMillis = event.startAt
                }.get(Calendar.MONTH) + 1
            } else {
                now.get(Calendar.MONTH) + 1
            }
        )
    }
    var startDay by remember { 
        mutableStateOf(
            if (event.startAt != null) {
                Calendar.getInstance(TimeZone.getTimeZone("Asia/Seoul")).apply {
                    timeInMillis = event.startAt
                }.get(Calendar.DAY_OF_MONTH)
            } else {
                now.get(Calendar.DAY_OF_MONTH)
            }
        )
    }
    var startHour by remember { 
        mutableStateOf(
            if (event.startAt != null) {
                Calendar.getInstance(TimeZone.getTimeZone("Asia/Seoul")).apply {
                    timeInMillis = event.startAt
                }.get(Calendar.HOUR_OF_DAY)
            } else {
                now.get(Calendar.HOUR_OF_DAY)
            }
        )
    }
    var startMinute by remember { 
        mutableStateOf(
            if (event.startAt != null) {
                Calendar.getInstance(TimeZone.getTimeZone("Asia/Seoul")).apply {
                    timeInMillis = event.startAt
                }.get(Calendar.MINUTE)
            } else {
                now.get(Calendar.MINUTE)
            }
        )
    }
    
    // 종료 시간 파싱
    var endYear by remember { 
        mutableStateOf(
            if (event.endAt != null) {
                Calendar.getInstance(TimeZone.getTimeZone("Asia/Seoul")).apply {
                    timeInMillis = event.endAt
                }.get(Calendar.YEAR)
            } else {
                Calendar.getInstance(TimeZone.getTimeZone("Asia/Seoul")).apply {
                    timeInMillis = event.startAt ?: System.currentTimeMillis()
                    add(Calendar.HOUR_OF_DAY, 1)
                }.get(Calendar.YEAR)
            }
        )
    }
    var endMonth by remember { 
        mutableStateOf(
            if (event.endAt != null) {
                Calendar.getInstance(TimeZone.getTimeZone("Asia/Seoul")).apply {
                    timeInMillis = event.endAt
                }.get(Calendar.MONTH) + 1
            } else {
                Calendar.getInstance(TimeZone.getTimeZone("Asia/Seoul")).apply {
                    timeInMillis = event.startAt ?: System.currentTimeMillis()
                    add(Calendar.HOUR_OF_DAY, 1)
                }.get(Calendar.MONTH) + 1
            }
        )
    }
    var endDay by remember { 
        mutableStateOf(
            if (event.endAt != null) {
                Calendar.getInstance(TimeZone.getTimeZone("Asia/Seoul")).apply {
                    timeInMillis = event.endAt
                }.get(Calendar.DAY_OF_MONTH)
            } else {
                Calendar.getInstance(TimeZone.getTimeZone("Asia/Seoul")).apply {
                    timeInMillis = event.startAt ?: System.currentTimeMillis()
                    add(Calendar.HOUR_OF_DAY, 1)
                }.get(Calendar.DAY_OF_MONTH)
            }
        )
    }
    var endHour by remember { 
        mutableStateOf(
            if (event.endAt != null) {
                Calendar.getInstance(TimeZone.getTimeZone("Asia/Seoul")).apply {
                    timeInMillis = event.endAt
                }.get(Calendar.HOUR_OF_DAY)
            } else {
                Calendar.getInstance(TimeZone.getTimeZone("Asia/Seoul")).apply {
                    timeInMillis = event.startAt ?: System.currentTimeMillis()
                    add(Calendar.HOUR_OF_DAY, 1)
                }.get(Calendar.HOUR_OF_DAY)
            }
        )
    }
    var endMinute by remember { 
        mutableStateOf(
            if (event.endAt != null) {
                Calendar.getInstance(TimeZone.getTimeZone("Asia/Seoul")).apply {
                    timeInMillis = event.endAt
                }.get(Calendar.MINUTE)
            } else {
                Calendar.getInstance(TimeZone.getTimeZone("Asia/Seoul")).apply {
                    timeInMillis = event.startAt ?: System.currentTimeMillis()
                    add(Calendar.HOUR_OF_DAY, 1)
                }.get(Calendar.MINUTE)
            }
        )
    }
    
    // 해당 월의 마지막 날짜 계산
    fun getDaysInMonth(year: Int, month: Int): Int {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Seoul"))
        calendar.set(Calendar.YEAR, year)
        calendar.set(Calendar.MONTH, month - 1)
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        return calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    }
    
    val maxStartDay = remember(startYear, startMonth) { getDaysInMonth(startYear, startMonth) }
    val maxEndDay = remember(endYear, endMonth) { getDaysInMonth(endYear, endMonth) }
    
    // 월이 변경되면 일 수가 줄어드는 경우 처리
    LaunchedEffect(maxStartDay) {
        if (startDay > maxStartDay) {
            startDay = maxStartDay
        }
    }
    
    LaunchedEffect(maxEndDay) {
        if (endDay > maxEndDay) {
            endDay = maxEndDay
        }
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("일정 수정") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("제목") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("장소") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text("본문") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5,
                )
                
                Text("시작 시간", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 년
                    OutlinedTextField(
                        value = startYear.toString(),
                        onValueChange = { startYear = it.toIntOrNull() ?: startYear },
                        label = { Text("년") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    // 월
                    OutlinedTextField(
                        value = startMonth.toString(),
                        onValueChange = { startMonth = it.toIntOrNull()?.coerceIn(1, 12) ?: startMonth },
                        label = { Text("월") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    // 일
                    OutlinedTextField(
                        value = startDay.toString(),
                        onValueChange = { startDay = it.toIntOrNull()?.coerceIn(1, maxStartDay) ?: startDay },
                        label = { Text("일") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 시
                    OutlinedTextField(
                        value = startHour.toString(),
                        onValueChange = { startHour = it.toIntOrNull()?.coerceIn(0, 23) ?: startHour },
                        label = { Text("시") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    // 분
                    OutlinedTextField(
                        value = startMinute.toString(),
                        onValueChange = { startMinute = it.toIntOrNull()?.coerceIn(0, 59) ?: startMinute },
                        label = { Text("분") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
                
                Text("종료 시간", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = endYear.toString(),
                        onValueChange = { endYear = it.toIntOrNull() ?: endYear },
                        label = { Text("년") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = endMonth.toString(),
                        onValueChange = { endMonth = it.toIntOrNull()?.coerceIn(1, 12) ?: endMonth },
                        label = { Text("월") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = endDay.toString(),
                        onValueChange = { endDay = it.toIntOrNull()?.coerceIn(1, maxEndDay) ?: endDay },
                        label = { Text("일") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = endHour.toString(),
                        onValueChange = { endHour = it.toIntOrNull()?.coerceIn(0, 23) ?: endHour },
                        label = { Text("시") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = endMinute.toString(),
                        onValueChange = { endMinute = it.toIntOrNull()?.coerceIn(0, 59) ?: endMinute },
                        label = { Text("분") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val startCalendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Seoul")).apply {
                        set(Calendar.YEAR, startYear)
                        set(Calendar.MONTH, startMonth - 1)
                        set(Calendar.DAY_OF_MONTH, startDay)
                        set(Calendar.HOUR_OF_DAY, startHour)
                        set(Calendar.MINUTE, startMinute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    val endCalendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Seoul")).apply {
                        set(Calendar.YEAR, endYear)
                        set(Calendar.MONTH, endMonth - 1)
                        set(Calendar.DAY_OF_MONTH, endDay)
                        set(Calendar.HOUR_OF_DAY, endHour)
                        set(Calendar.MINUTE, endMinute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    
                    val updatedEvent = event.copy(
                        title = title,
                        location = location.ifBlank { null },
                        body = body.ifBlank { null },
                        startAt = startCalendar.timeInMillis,
                        endAt = endCalendar.timeInMillis,
                        status = "confirmed" // 수정 시 자동으로 confirmed로 변경
                    )
                    onSave(updatedEvent)
                }
            ) {
                Text("저장")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        }
    )
}

