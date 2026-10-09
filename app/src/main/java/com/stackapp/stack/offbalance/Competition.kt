package com.stackapp.stack.offbalance

import android.content.Context
import com.stackapp.stack.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

fun competitionDay()=LocalDate.now(ZoneOffset.UTC).toString()
fun usernameKey(name:String)=name.trim().lowercase(Locale.ROOT)
fun validUsername(name:String)=name.matches(Regex("^[A-Za-z0-9_]{3,20}$"))
data class Competitor(val id:String,val username:String,val best:Map<String,Int>,val day:String,val daily:Map<String,Int>) {
    fun score(mode:Difficulty,today:Boolean=false)=if(today && day!=competitionDay())0 else (if(today)daily else best)[mode.name]?:0
}
class NameUnavailable:IllegalArgumentException()
interface CompetitionRepository {
    val available:Boolean
    suspend fun own():Competitor?
    suspend fun claim(name:String):Competitor
    suspend fun publish(mode:Difficulty,best:Int,today:Int):Competitor?
    suspend fun search(prefix:String):List<Competitor>
    suspend fun load(ids:Set<String>):List<Competitor>
    suspend fun remove()
}
object CompetitionFactory {
    // Instrumented journeys inject controlled server responses without publishing test players.
    var testRepository:CompetitionRepository?=null
    fun create():CompetitionRepository = if(BuildConfig.DEBUG && testRepository!=null)testRepository!! else
        if(BuildConfig.USE_FIREBASE && runCatching{FirebaseApp.getInstance()}.isSuccess)CloudCompetition() else OfflineCompetition()
}
class OfflineCompetition:CompetitionRepository {
    override val available=false
    override suspend fun own():Competitor?=null
    override suspend fun claim(name:String):Competitor=throw IllegalStateException("Connection unavailable")
    override suspend fun publish(mode:Difficulty,best:Int,today:Int):Competitor?=null
    override suspend fun search(prefix:String)=emptyList<Competitor>()
    override suspend fun load(ids:Set<String>)=emptyList<Competitor>()
    override suspend fun remove()=Unit
}
class CloudCompetition(private val db:FirebaseFirestore=FirebaseFirestore.getInstance(),private val auth:FirebaseAuth=FirebaseAuth.getInstance()):CompetitionRepository {
    override val available=true
    private suspend fun uid()=auth.currentUser?.uid ?: auth.signInAnonymously().await().user!!.uid
    private val empty=Difficulty.entries.associate{it.name to 0}
    private fun decode(doc:com.google.firebase.firestore.DocumentSnapshot):Competitor? {
        val name=doc.getString("username")?:return null
        fun scores(field:String)=Difficulty.entries.associate{it.name to ((doc.get(field) as? Map<*,*>)?.get(it.name) as? Number)?.toInt().let{n->n?:0}}
        return Competitor(doc.id,name,scores("best"),doc.getString("day").orEmpty(),scores("daily"))
    }
    override suspend fun own()=decode(db.collection("players").document(uid()).get(Source.SERVER).await())
    override suspend fun claim(name:String):Competitor {
        require(validUsername(name));val id=uid();val key=usernameKey(name)
        val player=db.collection("players").document(id);val handle=db.collection("handles").document(key)
        db.runTransaction { tx ->
            val reservation=tx.get(handle);val previous=tx.get(player)
            if(reservation.exists() && reservation.getString("owner")!=id)throw NameUnavailable()
            val oldKey=previous.getString("key")
            // All reads precede writes; old handles are released atomically when renamed.
            tx.set(handle,mapOf("owner" to id,"username" to name,"key" to key))
            if(oldKey!=null && oldKey!=key)tx.delete(db.collection("handles").document(oldKey))
            tx.set(player,mapOf("username" to name,"key" to key,"best" to (previous.get("best")?:empty),"day" to (previous.getString("day")?:competitionDay()),"daily" to (previous.get("daily")?:empty)))
        }.await()
        return own()!!
    }
    override suspend fun publish(mode:Difficulty,best:Int,today:Int):Competitor? {
        if(best<=0)return own()
        val ref=db.collection("players").document(uid())
        db.runTransaction { tx ->
            val old=tx.get(ref);if(!old.exists())return@runTransaction
            fun scores(field:String)=Difficulty.entries.associate{it.name to (((old.get(field) as? Map<*,*>)?.get(it.name) as? Number)?.toInt()?:0)}.toMutableMap()
            val records=scores("best");records[mode.name]=maxOf(records[mode.name]?:0,best)
            val daily=if(old.getString("day")==competitionDay())scores("daily") else empty.toMutableMap()
            daily[mode.name]=maxOf(daily[mode.name]?:0,today)
            tx.update(ref,mapOf("best" to records,"day" to competitionDay(),"daily" to daily))
        }.await()
        return decode(ref.get(Source.SERVER).await())
    }
    override suspend fun search(prefix:String):List<Competitor> {
        val key=usernameKey(prefix);if(key.length<2)return emptyList()
        val me=uid()
        return db.collection("players").orderBy("key").startAt(key).endAt(key+"\uf8ff").limit(8).get(Source.SERVER).await().documents.mapNotNull(::decode).filter{it.id!=me}
    }
    override suspend fun load(ids:Set<String>):List<Competitor> = ids.take(30).mapNotNull {decode(db.collection("players").document(it).get(Source.SERVER).await())}
    override suspend fun remove(){
        val id=uid();val ref=db.collection("players").document(id)
        db.runTransaction{tx->val current=tx.get(ref);current.getString("key")?.let{tx.delete(db.collection("handles").document(it))};tx.delete(ref)}.await()
        // Removing the public profile does not revoke the optional Google login.
        if(auth.currentUser?.isAnonymous==true)auth.currentUser?.delete()?.await()
    }
}
class RivalStore(context:Context) {
    private val prefs=context.applicationContext.getSharedPreferences("rivals",Context.MODE_PRIVATE)
    fun ids()=prefs.getStringSet("following",emptySet()).orEmpty().toSet()
    fun follow(id:String){prefs.edit().putStringSet("following",(ids()+id).take(30).toSet()).apply()}
    fun unfollow(id:String){prefs.edit().putStringSet("following",ids()-id).apply()}
}
