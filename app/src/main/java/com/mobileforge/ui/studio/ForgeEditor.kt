package com.mobileforge.ui.studio

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobileforge.engine.Suggestion
import com.mobileforge.ui.theme.MfCyan
import com.mobileforge.ui.theme.MfLine
import com.mobileforge.ui.theme.MfMuted
import com.mobileforge.ui.theme.MfPanel
import com.mobileforge.ui.theme.MfPanel2
import com.mobileforge.ui.theme.MfPurple
import com.mobileforge.ui.theme.MfText

@Composable
fun ForgeEditor(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    suggestions: List<Suggestion>,
    onSuggestion: (Suggestion) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(MfPanel)
                .padding(12.dp),
            textStyle = TextStyle(
                color = MfText,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                fontFamily = FontFamily.Monospace,
            ),
            cursorBrush = SolidColor(MfCyan),
        )
        if (suggestions.isNotEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(MfPanel2)
                    .border(1.dp, MfPurple.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    "Автодополнение",
                    color = MfMuted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
                suggestions.forEach { item ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSuggestion(item) }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    ) {
                        Text(item.label, color = MfPurple, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                        Text(item.detail, color = MfMuted, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
