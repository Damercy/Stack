package com.stackapp.stack.offbalance

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI

/** Private account details; public competition uses only the chosen username. */
@Composable fun AccountIdentityCard(account:AccountState,username:String?,modifier:Modifier=Modifier) {
    val palette=LocalBalancePalette.current
    val photo by produceState<ImageBitmap?>(null,account.photoUrl) {
        value=null
        if(account.photoUrl.isNotBlank())value=withContext(Dispatchers.IO){loadProfilePhoto(account.photoUrl)}
    }
    Column(modifier.fillMaxWidth().border(2.dp,palette.ink).background(palette.paper).padding(16.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(64.dp).clip(CircleShape).background(palette.primary),contentAlignment=Alignment.Center) {
                val image=photo
                if(image!=null)Image(image,"Google profile photo",Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
                else Utility(account.name.take(1).uppercase().ifBlank{"G"},color=palette.paper,size=24)
            }
            Column(Modifier.weight(1f)) {
                Utility(account.name.ifBlank{"GOOGLE ACCOUNT"},size=15)
                if(account.email.isNotBlank()){Spacer(Modifier.height(6.dp));Utility(account.email,size=10)}
            }
        }
        Spacer(Modifier.height(14.dp))
        Utility(username?.let{"@$it"} ?: "CHOOSE YOUR PUBLIC USERNAME",size=12,color=palette.primary)
    }
}

private fun loadProfilePhoto(url:String):ImageBitmap?=runCatching {
    val uri=URI(url)
    if(uri.scheme!="https" || uri.host==null || uri.userInfo!=null)return null
    val connection=uri.toURL().openConnection() as HttpURLConnection
    try {
        connection.connectTimeout=5_000;connection.readTimeout=5_000;connection.instanceFollowRedirects=false
        if(connection.responseCode!=200 || !connection.contentType.orEmpty().startsWith("image/") || connection.contentLengthLong>524_288)return null
        val bytes=connection.inputStream.use{input->
            val output=java.io.ByteArrayOutputStream();val buffer=ByteArray(8_192)
            while(output.size()<=524_288){val count=input.read(buffer,0,minOf(buffer.size,524_289-output.size()));if(count<0)break;output.write(buffer,0,count)}
            output.toByteArray()
        }
        if(bytes.size>524_288)return null
        val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true}
        BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
        if(bounds.outWidth<=0 || bounds.outHeight<=0)return null
        var sample=1
        while(bounds.outWidth/sample>256 || bounds.outHeight/sample>256)sample*=2
        BitmapFactory.decodeByteArray(bytes,0,bytes.size,BitmapFactory.Options().apply{inSampleSize=sample})?.asImageBitmap()
    } finally {connection.disconnect()}
}.getOrNull()
