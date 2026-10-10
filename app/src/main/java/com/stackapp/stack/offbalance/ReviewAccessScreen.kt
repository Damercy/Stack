package com.stackapp.stack.offbalance

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.*
import kotlinx.coroutines.*

@Composable fun ColumnScope.ReviewAccessScreen(open:(String)->Unit) {
    val context=LocalContext.current
    val palette=LocalBalancePalette.current
    val keyboard=LocalSoftwareKeyboardController.current
    val scope=rememberCoroutineScope()
    var code by remember{mutableStateOf("")}
    var busy by remember{mutableStateOf(false)}
    var error by remember{mutableStateOf(false)}
    PosterFit("REVIEW",color=palette.ink)
    Spacer(Modifier.height(24.dp))
    Utility("ENTER THE PROVIDED ACCESS CODE.",size=11)
    Spacer(Modifier.height(16.dp))
    BasicTextField(code,{if(!busy){code=it.take(160);error=false}},
        Modifier.fillMaxWidth().border(2.dp,palette.ink).background(palette.paper).padding(18.dp)
            .semantics{contentDescription="Review access code"}.testTag("review_access_code"),
        enabled=!busy,singleLine=true,textStyle=TextStyle(fontFamily=UtilityFont,fontSize=18.sp,color=palette.ink),
        cursorBrush=SolidColor(palette.accent),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password),
        visualTransformation=PasswordVisualTransformation(),
        decorationBox={inner->if(code.isEmpty())Utility("ACCESS CODE",color=palette.ink.copy(alpha=.4f));inner()})
    Spacer(Modifier.height(16.dp))
    if(error)Utility("CODE NOT RECOGNIZED. TRY AGAIN.",color=palette.accent,size=11)
    Spacer(Modifier.weight(1f))
    Utility("SAMPLE DATA. NO PURCHASE OR SIGN-IN.",size=10)
    Spacer(Modifier.height(16.dp))
    Action(if(busy)"CHECKING…" else "CONTINUE"){
        if(!busy){
            busy=true
            scope.launch {
                val submitted=code.trim()
                val accepted=withContext(Dispatchers.Default){ReviewerAccess.valid(context,submitted)}
                busy=false
                if(accepted){keyboard?.hide();code="";open(submitted)}else error=true
            }
        }
    }
}
