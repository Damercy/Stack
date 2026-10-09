package com.stackapp.stack.offbalance

import android.content.Context
import android.media.*
import android.os.Handler
import android.os.Looper
import com.stackapp.stack.R
import kotlinx.coroutines.*
import androidx.compose.runtime.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Decode and upload static loops off the UI thread; one owner controls playback. */
class BalanceAudio(context: Context) {
    private val context=context.applicationContext
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private val manager=context.getSystemService(AudioManager::class.java)
    private var player:AudioTrack?=null
    private var load:Job?=null
    private var selected=-1
    private var volume=.7f
    private var wanted=false
    private var focused=false
    private var ducked=false
    private var closed=false
    var state by mutableStateOf("Stopped"); private set
    /** Read-only playback diagnostics used by device tests across loop boundaries. */
    val playbackPosition:Int get()=player?.playbackHeadPosition ?: 0
    private val focus=AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(attributes())
        .setOnAudioFocusChangeListener({ change ->
            if(!closed && wanted)when(change){
                AudioManager.AUDIOFOCUS_GAIN -> {focused=true;ducked=false;applyPlayback()}
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {ducked=true;applyPlayback()}
                else -> {focused=false;applyPlayback()}
            }
        },Handler(Looper.getMainLooper())).build()
    private val sound=SoundPool.Builder().setMaxStreams(3).setAudioAttributes(attributes()).build()
    private var effectReady=false
    private val hit:Int
    init {
        sound.setOnLoadCompleteListener {_,_,status -> effectReady=status==0}
        hit=sound.load(context,R.raw.stack_crystal,1)
    }
    fun impact(level:Float) { if(effectReady && !closed && level>0f)sound.play(hit,level*.5f,level*.5f,1,0,1f) }
    fun update(track:Int,level:Float,playing:Boolean) {
        if(closed)return
        volume=level.coerceIn(0f,1f);wanted=playing && volume>0f
        if(wanted && !focused)focused=manager.requestAudioFocus(focus)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if(!wanted){player?.pause();manager.abandonAudioFocusRequest(focus);focused=false;ducked=false}
        if(selected!=track || player==null && load?.isActive!=true) {
            selected=track;load?.cancel();val old=player;player=null;release(old)
            state=if(wanted)"Loading" else "Stopped"
            load=scope.launch(Dispatchers.IO) {
                var created:AudioTrack?=null
                try {
                    val pcm=decode(track);require(pcm.isNotEmpty()) {"Empty groove"}
                    val ready=AudioTrack.Builder().setAudioAttributes(attributes())
                        .setAudioFormat(AudioFormat.Builder().setSampleRate(22050).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                        .setBufferSizeInBytes(pcm.size).setTransferMode(AudioTrack.MODE_STATIC).build()
                    created=ready;check(ready.state!=AudioTrack.STATE_UNINITIALIZED)
                    check(ready.write(pcm,0,pcm.size)==pcm.size)
                    check(ready.state==AudioTrack.STATE_INITIALIZED)
                    check(ready.setLoopPoints(0,pcm.size/2,-1)==AudioTrack.SUCCESS)
                    withContext(Dispatchers.Main.immediate){if(!closed && selected==track){player=ready;created=null;applyPlayback()}}
                } catch(cancelled:CancellationException){throw cancelled}
                catch(error:Exception){android.util.Log.w("BalanceAudio","Could not prepare groove",error);withContext(Dispatchers.Main.immediate){state="Unavailable"}}
                finally{created?.release()}
            }
        }
        applyPlayback()
    }
    /** A tap may reacquire focus after a permanent external interruption. */
    fun interaction(){if(wanted && player==null && load?.isActive!=true)update(selected,volume,wanted)
        else if(wanted && !focused) {focused=manager.requestAudioFocus(focus)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED;applyPlayback()}}
    private fun applyPlayback(){
        val current=player ?: return
        current.setVolume(volume*if(ducked).22f else 1f)
        if(wanted && focused){if(current.playState!=AudioTrack.PLAYSTATE_PLAYING)current.play();state="Playing"}
        else {if(current.playState==AudioTrack.PLAYSTATE_PLAYING)current.pause();state="Paused"}
    }
    private fun decode(track:Int):ByteArray {
        val id=listOf(R.raw.groove_0,R.raw.groove_1,R.raw.groove_2,R.raw.groove_3,R.raw.groove_4,R.raw.groove_5)[track.coerceIn(0,5)]
        val bytes=context.resources.openRawResource(id).use{it.readBytes()};var pos=12
        while(pos+8<=bytes.size){
            val len=ByteBuffer.wrap(bytes,pos+4,4).order(ByteOrder.LITTLE_ENDIAN).int
            if(len<0 || pos+8L+len>bytes.size)break
            if(String(bytes,pos,4,Charsets.US_ASCII)=="data")return bytes.copyOfRange(pos+8,pos+8+len)
            pos+=8+len+(len%2)
        }
        return ByteArray(0)
    }
    private fun release(track:AudioTrack?){if(track!=null)scope.launch(Dispatchers.IO+NonCancellable){track.release()}}
    fun close(){closed=true;load?.cancel();val old=player;player=null;release(old);sound.release();manager.abandonAudioFocusRequest(focus);scope.cancel();state="Stopped"}
    private fun attributes()=AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
}
