package com.mobileforge.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobileforge.ui.theme.MfCyan
import com.mobileforge.ui.theme.MfDanger
import com.mobileforge.ui.theme.MfLine
import com.mobileforge.ui.theme.MfMuted
import com.mobileforge.ui.theme.MfPanel
import com.mobileforge.ui.theme.MfPanel2
import com.mobileforge.ui.theme.MfPurple
import com.mobileforge.ui.theme.MfText

@Composable
fun MfButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    danger: Boolean = false,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = when {
                danger -> MfDanger.copy(alpha = 0.25f)
                primary -> MfPurple.copy(alpha = 0.55f)
                else -> MfPanel2
            },
            contentColor = MfText,
        ),
        shape = RoundedCornerShape(9.dp),
    ) { Text(text, fontSize = 13.sp) }
}

@Composable
fun MfTextButton(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) { Text(text, color = MfPurple) }
}

@Composable
fun MfField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    password: Boolean = false,
    numeric: Boolean = false,
    minLines: Int = 1,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = MfMuted, fontSize = 12.sp) },
        modifier = modifier.fillMaxWidth(),
        singleLine = singleLine,
        minLines = minLines,
        visualTransformation = if (password) androidx.compose.ui.text.input.PasswordVisualTransformation()
        else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = MfText,
            unfocusedTextColor = MfText,
            focusedBorderColor = MfPurple,
            unfocusedBorderColor = MfLine,
            focusedContainerColor = MfPanel,
            unfocusedContainerColor = MfPanel,
            cursorColor = MfCyan,
        ),
        shape = RoundedCornerShape(8.dp),
    )
}

@Composable
fun MfCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MfPanel)
            .border(1.dp, MfLine, RoundedCornerShape(12.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(15.dp),
    ) { content() }
}

@Composable
fun MfHero(title: String, hint: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MfPanel2)
            .border(1.dp, MfPurple.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .padding(20.dp),
    ) {
        Text(title, color = MfText, fontSize = 24.sp)
        Text(hint, color = MfMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
fun FlowRowWrap(content: @Composable RowScope.() -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp), content = content)
}
