package com.example.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun DebouncedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    debounceMs: Long = 500L,
    minLines: Int = 1,
    maxLines: Int = Int.MAX_VALUE,
    textStyle: TextStyle = TextStyle(fontSize = 13.sp),
    singleLine: Boolean = false,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation = androidx.compose.ui.text.input.VisualTransformation.None
) {
    var localValue by remember { mutableStateOf(value) }
    var isStale by remember { mutableStateOf(false) }

    LaunchedEffect(value) {
        if (value != localValue) {
            localValue = value
        }
    }

    LaunchedEffect(localValue) {
        if (localValue != value) {
            isStale = true
            delay(debounceMs)
            onValueChange(localValue)
            isStale = false
        }
    }

    OutlinedTextField(
        value = localValue,
        onValueChange = { localValue = it },
        placeholder = {
            if (placeholder.isNotEmpty()) {
                Text(placeholder, fontSize = 12.sp)
            }
        },
        modifier = modifier.fillMaxWidth(),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = if (isStale) Color(0xFFFF9800) else Color.Unspecified
        ),
        shape = RoundedCornerShape(12.dp),
        textStyle = textStyle,
        minLines = minLines,
        maxLines = maxLines,
        singleLine = singleLine,
        visualTransformation = visualTransformation
    )
}
