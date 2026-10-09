package com.stackapp.stack.offbalance
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Trace
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.testTag
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import com.stackapp.stack.tap.AndroidHapticEngine
import kotlinx.coroutines.*
import kotlin.math.abs
private enum class Screen { HOME, PLAY, ONBOARDING, ACCOUNT, DIFFICULTY, TRIALS, MUSIC, SETTINGS, TODAY, RIVALS, PROFILE, COUNTRY, STYLE }
@Composable fun OffBalanceApp(context:Context, initialName:String?, initialCountry:String, saveName:(String)->Unit, saveCountry:(String)->Unit, onLanding:suspend ()->Unit, openRivalsRequest:Int=0) {
    val prefs=remember { BalancePreferences(context) }
    val analytics=remember{BalanceAnalytics(context)}
    val review=remember{BalanceReview(context,prefs,analytics)}
    val account=remember{BalanceAccountFactory.create(context)}
    val accountState by account.state.collectAsState()
    val remote=remember{BalanceRemoteConfig(context)}
    val config by remote.state.collectAsState()
    val configUpdate by remote.updateRevision.collectAsState()
    val store=remember{StylePackFactory.create(context)}
    val pack by store.state.collectAsState()
    var skin by remember{mutableStateOf(prefs.skin)}
    val palette=if(pack.owned)skin.palette else BalanceSkin.GOLD.palette
    val Sun=palette.background;val Cobalt=palette.primary;val Vermilion=palette.accent;val Ink=palette.ink;val Cream=palette.paper
    SideEffect{
        (context as? android.app.Activity)?.window?.let { window ->
            androidx.core.view.WindowCompat.getInsetsController(window,window.decorView).apply {
                isAppearanceLightStatusBars=palette.background!=BalanceSkin.NIGHT.palette.background
                isAppearanceLightNavigationBars=isAppearanceLightStatusBars
            }
            @Suppress("DEPRECATION")
            window.navigationBarColor=android.graphics.Color.argb(255,(Sun.red*255).toInt(),(Sun.green*255).toInt(),(Sun.blue*255).toInt())
        }
    }
    val tilt=remember { BalanceTilt(context) }
    val audio=remember { BalanceAudio(context) }
    val haptic=remember { AndroidHapticEngine(context) }
    val scope=rememberCoroutineScope()
    val social=remember{CompetitionFactory.create()}
    val rivals=remember{RivalStore(context)}
    val reminders=remember{BalanceReminders(context)}
    var reminderOn by remember{mutableStateOf(reminders.enabled && reminders.permitted())}
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->reminderOn=granted;reminders.enabled=granted}
    var own by remember{mutableStateOf<Competitor?>(null)}
    var followed by remember{mutableStateOf(rivals.ids())}
    var people by remember{mutableStateOf(emptyList<Competitor>())}
    var query by rememberSaveable{mutableStateOf("")}
    var results by remember{mutableStateOf(emptyList<Competitor>())}
    var socialError by remember{mutableStateOf("")}
    var loadingSocial by remember{mutableStateOf(false)}
    var searching by remember{mutableStateOf(false)}
    var searchError by remember{mutableStateOf("")}
    var refresh by remember{mutableIntStateOf(0)}
    var profileBusy by remember{mutableStateOf(false)}
    var confirmDelete by remember{mutableStateOf(false)}
    var difficulty by remember { mutableStateOf(prefs.difficulty) }
    var tutorial by remember { mutableStateOf(false) }
    var onboardingStep by remember { mutableIntStateOf(prefs.onboardingStep) }
    var onboardingPlaying by remember { mutableStateOf(false) }
    var onboardingReplay by remember { mutableStateOf(false) }
    var trial by remember { mutableIntStateOf(-1) }
    var trialSelected by remember { mutableIntStateOf(prefs.trialsComplete.coerceAtMost(3)) }
    var paused by rememberSaveable { mutableStateOf(false) }
    var music by remember { mutableFloatStateOf(prefs.music) }
    var effects by remember { mutableFloatStateOf(prefs.effects) }
    var sensitivity by remember { mutableFloatStateOf(prefs.sensitivity) }
    var touch by remember { mutableStateOf(prefs.touch || !tilt.available) }
    var vibration by remember { mutableStateOf(prefs.haptics) }
    var track by remember { mutableIntStateOf(prefs.track) }
    var autoMusic by remember { mutableStateOf(prefs.autoMusic) }
    var preview by remember { mutableStateOf(false) }
    var displayName by rememberSaveable { mutableStateOf(initialName.orEmpty()) }
    var country by rememberSaveable { mutableStateOf(initialCountry) }
    var profileError by remember { mutableStateOf("") }
    var foreground by remember { mutableStateOf(true) }
    var score by remember { mutableIntStateOf(0) }
    var started by remember { mutableStateOf(false) }
    var down by remember { mutableStateOf(false) }
    var won by remember { mutableStateOf(false) }
    val navigation=remember { mutableStateListOf(if(!prefs.introduced)Screen.ONBOARDING else Screen.HOME) }
    val current=navigation.last()
    var game by remember { mutableStateOf(BalancePhysics(difficulty,tutorial,trial)) }
    val frozen=remember { mutableStateOf(game.snapshot()) }
    var runConfig by remember{mutableStateOf(config)}
    var runBest by remember{mutableIntStateOf(prefs.best(difficulty))}
    var runEnded by remember{mutableStateOf(false)}
    var newBest by remember{mutableStateOf(false)}
    var reward by remember{mutableStateOf<BalanceReward?>(null)}
    var rewardId by remember{mutableIntStateOf(0)}
    var recoveryShown by remember{mutableStateOf(false)}
    var offerShown by remember{mutableStateOf(false)}
    val showOffer=(down || won) && offerShown && config.payments && config.verificationReady && pack.ready && !pack.owned
    LaunchedEffect(config.googleSignIn){account.configure(config.googleSignIn)}
    LaunchedEffect(accountState.revision,accountState.signedIn){
        if(accountState.revision>0){own=null;displayName="";saveName("");socialError="";refresh++}
        if(accountState.signedIn)refresh++
    }
    LaunchedEffect(current,down,won,foreground,reward,showOffer,config.reviews,config.reviewMinRuns,config.reviewAgeHours){
        if(!foreground || reward!=null || showOffer || !prefs.introduced)return@LaunchedEffect
        val activity=context as? android.app.Activity ?: return@LaunchedEffect
        val result=current==Screen.PLAY && (down || won)
        if(!result && current!=Screen.HOME)return@LaunchedEffect
        if(result && down)while(!game.collapseFinished){delay(100)}
        delay(if(result)800 else 1_500)
        review.consider(activity,config,ReviewMoment(newBest,score,prefs.trialsComplete,current==Screen.HOME)){
            foreground && navigation.last()==current && reward==null && !showOffer && (current!=Screen.PLAY || down || won)
        }
    }
    LaunchedEffect(current,foreground,config,configUpdate,onboardingStep){if(foreground && current!=Screen.PLAY){remote.boundary();store.sync(config);reminders.sync()}}
    DisposableEffect(Unit){onDispose{remote.close();store.close();account.close()}}
    val sensorActive=foreground && (current==Screen.ONBOARDING && onboardingStep==1 || current==Screen.PLAY && !paused && !down && !won)
    val running=current==Screen.PLAY && sensorActive && started
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(openRivalsRequest){if(openRivalsRequest>0){navigation.clear();navigation.add(Screen.HOME);navigation.add(if(config.friends)Screen.RIVALS else Screen.TODAY)}}
    LaunchedEffect(current,foreground,down,refresh,followed,accountState.revision,accountState.signedIn){
        if(!config.friends || !foreground || current==Screen.PLAY && !down)return@LaunchedEffect
        if(!social.available)return@LaunchedEffect
        loadingSocial=true;socialError=""
        try {
            own=social.own()
            own?.let{player->prefs.restoreRecords(player);if(accountState.signedIn){displayName=player.username;saveName(player.username)}}
            if(own!=null){Difficulty.entries.forEach{mode->own=social.publish(mode,prefs.best(mode),prefs.today(mode))}}
            people=social.load(followed)
        } catch(cancelled:CancellationException){throw cancelled}
        catch(_:Exception){socialError="Couldn’t connect. Try again."}
        finally{loadingSocial=false}
    }
    LaunchedEffect(query,current,refresh){
        if(!config.friends || current!=Screen.RIVALS)return@LaunchedEffect
        results=emptyList();searchError="";searching=query.trim().length>=2 && social.available
        if(!searching)return@LaunchedEffect
        delay(300)
        try{results=social.search(query).filter{it.id!=own?.id}}catch(cancelled:CancellationException){throw cancelled}
        catch(_:Exception){searchError="Search unavailable. Retry."}finally{searching=false}
    }
    DisposableEffect(lifecycle) {
        val observer=LifecycleEventObserver { _,event ->
            when(event){
                Lifecycle.Event.ON_RESUME -> {foreground=true;reminders.played();reminders.sync()}
                Lifecycle.Event.ON_PAUSE -> {foreground=false;reward=null;if(navigation.last()==Screen.PLAY && !down)paused=true}
                else -> Unit
            }
        };lifecycle.addObserver(observer)
        onDispose {lifecycle.removeObserver(observer);tilt.stop();audio.close()}
    }
    DisposableEffect(sensorActive,touch) {if(sensorActive && !touch)tilt.start() else tilt.stop();onDispose{tilt.stop()}}
    LaunchedEffect(track,autoMusic,difficulty,music,current,running,preview,foreground,pack.owned,onboardingPlaying) {
        audio.update(if(autoMusic && current!=Screen.MUSIC)difficulty.ordinal else if(track<3 || pack.owned)track else 0,music,foreground && (running || current==Screen.MUSIC && preview || current==Screen.ONBOARDING && onboardingPlaying))
    }
    fun go(screen:Screen){if(screen==Screen.STYLE)analytics.event("style_pack_view",difficulty,score);if(screen==Screen.MUSIC && autoMusic)track=difficulty.ordinal;if(navigation.last()!=screen)navigation.add(screen)}
    fun back(){
        preview=false
        if(navigation.last()==Screen.ONBOARDING){
            if(onboardingStep>0){onboardingStep--;prefs.onboardingStep=onboardingStep}
            else {analytics.tutorial("tutorial_skip",onboardingStep,onboardingReplay);prefs.introduced=true;if(onboardingReplay && navigation.size>1){onboardingReplay=false;navigation.removeAt(navigation.lastIndex)}else {navigation.clear();navigation.add(Screen.HOME)}}
        } else if(navigation.last()==Screen.PLAY){
            if(down || won){paused=false;down=false;won=false;tutorial=false;navigation.clear();navigation.add(Screen.HOME)}
            else {paused=!paused;reward=null}
        } else if(navigation.size>1){if(navigation.last()==Screen.ACCOUNT)account.cancelSwitch();navigation.removeAt(navigation.lastIndex)}
    }
    fun start(selectedTrial:Int=-1, first:Boolean=false){
        runConfig=config;runBest=prefs.best(difficulty);runEnded=false;newBest=false;reward=null;recoveryShown=false;offerShown=false
        trial=selectedTrial;tutorial=first;paused=false;down=false;won=false;score=0;started=false
        game=BalancePhysics(difficulty,tutorial,trial);frozen.value=game.snapshot();reminders.played()
        navigation.clear();navigation.add(Screen.HOME);navigation.add(Screen.PLAY);tilt.calibrate()
    }
    fun finishOnboarding(skipped:Boolean){
        if(navigation.last()!=Screen.ONBOARDING)return
        analytics.tutorial(if(skipped)"tutorial_skip" else "tutorial_complete",onboardingStep,onboardingReplay)
        prefs.introduced=true;onboardingPlaying=false
        if(onboardingReplay){onboardingReplay=false;navigation.removeAt(navigation.lastIndex)} else if(skipped){navigation.clear();navigation.add(Screen.HOME)} else start()
    }
    LaunchedEffect(current){
        analytics.screen(current.name.lowercase())
        if(current==Screen.ONBOARDING && !prefs.onboardingStarted){prefs.onboardingStarted=true;analytics.tutorial("tutorial_begin",0,false)}
    }
    fun home(){if(!runEnded && started){prefs.completedRuns++;runEnded=true;analytics.event("tower_run_end",difficulty,score,"exit")};reward=null;paused=false;down=false;won=false;tutorial=false;navigation.clear();navigation.add(Screen.HOME)}
    fun celebrate(label:String){if(runConfig.celebrations){reward=BalanceReward(++rewardId,label);if(vibration)haptic.celebrate();analytics.event("tower_celebration",difficulty,score)}}
    fun endRun(completed:Boolean){
        if(runEnded)return
        runEnded=true;prefs.completedRuns++
        analytics.event("tower_run_end",difficulty,score,if(completed)"trial_complete" else "fall")
        newBest=trial<0 && score>runBest
        if(completed)celebrate("TRIAL COMPLETE") else if(newBest)celebrate("NEW BEST")
        offerShown=pack.ready && runConfig.offerEligible(prefs.completedRuns,newBest,completed,false,pack.owned,System.currentTimeMillis(),prefs.offerShownAt)
        if(offerShown){prefs.offerShownAt=System.currentTimeMillis();analytics.event("style_offer_shown",difficulty,score)}
    }
    fun landed(newScore:Int,recovered:Boolean,hold:Float){
        if(newScore!=score){score=newScore;if(vibration)haptic.clink(.55f);audio.impact(effects);scope.launch{onLanding()};if(trial<0)prefs.record(difficulty,newScore)
            if(newScore>0 && newScore%runConfig.milestoneEvery==0)celebrate("$newScore LAYERS")
        }
        if(recovered && !recoveryShown){recoveryShown=true;celebrate("NICE SAVE")}
        val complete=when(trial){0->newScore>=5;1->hold>=3;2->recovered;3->newScore>=8;else->false}
        if(complete && !won){won=true;if(trial>=0)prefs.trialsComplete=maxOf(prefs.trialsComplete,trial+1);endRun(true)}
    }
    val audioStatus=audio.state.lowercase()
    CompositionLocalProvider(LocalBalancePalette provides palette) {
    Box(Modifier.fillMaxSize().background(Sun).safeDrawingPadding().imePadding().semantics {testTagsAsResourceId=true;contentDescription="Off Balance. Music $audioStatus"}.testTag("balance_app")) {
        SharedTransitionLayout {
        CompositionLocalProvider(LocalBalanceShared provides this@SharedTransitionLayout) {
        NavDisplay(backStack=navigation,onBack={back()},modifier=Modifier.fillMaxSize(),
            transitionSpec={ (fadeIn(tween(130)) + slideInHorizontally(spring(.83f,650f)){it/5}) togetherWith (fadeOut(tween(90))+slideOutHorizontally(tween(160)){-it/8}) },
            popTransitionSpec={ (fadeIn(tween(100))+slideInHorizontally(spring(.85f,650f)){-it/6}) togetherWith (fadeOut(tween(100))+slideOutHorizontally(tween(150)){it/5}) },
            predictivePopTransitionSpec={ (fadeIn()+slideInHorizontally{-it/6}) togetherWith (fadeOut()+slideOutHorizontally{it/5}) },
            entryProvider={ screen -> NavEntry(screen) {
                when(screen){
                    Screen.ONBOARDING -> OnboardingScreen(onboardingStep,current==Screen.ONBOARDING && foreground,touch,
                        tilt={tilt.value},useTouch={touch=true;prefs.touch=true;analytics.event("tutorial_touch_selected",difficulty)},
                        onStep={next->if(next==onboardingStep+1){analytics.tutorial("tutorial_step_complete",onboardingStep,onboardingReplay);onboardingStep=next;prefs.onboardingStep=next;tilt.calibrate()}},
                        complete={finishOnboarding(false)},skip={finishOnboarding(true)},back={back()},
                        landed={audio.impact(effects);if(vibration)haptic.clink(.55f)},playing={onboardingPlaying=it},balanceBits=prefs.onboardingBalance,saveBalance={prefs.onboardingBalance=it},
                        account=accountState,signIn={(context as? android.app.Activity)?.let{activity->scope.launch{account.signIn(activity)}}},useSaved={scope.launch{account.useSavedProfile()}},cancelSwitch={account.cancelSwitch()})
                    Screen.ACCOUNT -> Page({account.cancelSwitch();back()},footer={
                        if(accountState.message.isNotBlank())Utility(accountState.message,Modifier.padding(vertical=12.dp),size=11)
                        when {
                            accountState.busy -> Utility("CONNECTING…",Modifier.padding(vertical=16.dp))
                            accountState.savedProfile -> {Action("USE SAVED PROFILE"){scope.launch{account.useSavedProfile()}};LinkRow("CANCEL"){account.cancelSwitch()}}
                            accountState.signedIn -> {Action("DONE"){back()};LinkRow("SIGN OUT"){scope.launch{account.signOut()}};LinkRow("DELETE ACCOUNT"){context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://stack-damercy.web.app/delete-account")))}}
                            accountState.configured -> {Action("CONTINUE WITH GOOGLE"){(context as? android.app.Activity)?.let{activity->scope.launch{account.signIn(activity)}}};LinkRow("KEEP PLAYING AS GUEST"){back()}}
                            else -> Action("KEEP PLAYING"){back()}
                        }
                    }) {
                        PosterFit(if(accountState.signedIn)"SAVED" else "YOUR GAME",color=Ink)
                        Spacer(Modifier.height(24.dp))
                        Utility(if(accountState.signedIn)"CONNECTED WITH GOOGLE" else "KEEP YOUR USERNAME AND RECORDS ACROSS DEVICES.",size=11)
                        if(accountState.signedIn){Spacer(Modifier.height(12.dp));Utility(if(loadingSocial)"LOADING YOUR RECORDS…" else own?.let{"PLAYING AS @${it.username}"} ?: "YOU'RE SIGNED IN. CHOOSE A USERNAME TO COMPETE.",size=11);if(!loadingSocial && own==null)LinkRow("CHOOSE USERNAME"){go(Screen.PROFILE)}}
                        Spacer(Modifier.height(18.dp));TowerDrawing(decorativeFrame(),Modifier.fillMaxWidth().height(200.dp),decorative=true)
                        Utility("Your Google name and email stay off the leaderboard. Choose a public username separately.",size=11)
                        Spacer(Modifier.height(16.dp));Utility("Guest play stays available. Signing out keeps device records here.",size=10)
                        Spacer(Modifier.weight(1f))
                    }
                    Screen.HOME -> HomeScreen(difficulty,prefs.best(difficulty),{go(Screen.SETTINGS)},{go(Screen.DIFFICULTY)},{start()},{if(config.trials)go(Screen.TRIALS)},{go(Screen.TODAY)},config.trials)
                    Screen.DIFFICULTY -> Page({back()}) {
                        PosterFit("DIFFICULTY",color=Ink);Spacer(Modifier.height(18.dp))
                        Difficulty.entries.forEach { mode ->
                            Rule();PressSurface(Modifier.fillMaxWidth().heightIn(min=116.dp),onClick={difficulty=mode;prefs.difficulty=mode}) {
                                Row(Modifier.fillMaxWidth().padding(vertical=14.dp),verticalAlignment=Alignment.CenterVertically){
                                    Selection(difficulty==mode);TowerDrawing(decorativeFrame(mode.ordinal),Modifier.width(100.dp).height(92.dp),decorative=true)
                                    Column(Modifier.weight(1f)){Poster(mode.title,color=Ink,size=32);Utility("${mode.label} · ${mode.description}",size=11)}
                                }
                            }
                        };Rule();Spacer(Modifier.weight(1f));Utility("RECORDS ARE SEPARATE FOR EACH MODE.",size=9);Spacer(Modifier.height(12.dp));Action("PLAY ${difficulty.title}"){start()}
                    }
                    Screen.TRIALS -> Page({back()}) {
                        PosterFit("TRIALS",color=Ink);Spacer(Modifier.height(22.dp))
                        val names=listOf("FIRST FIVE","ROUND TWO","SAVE IT","MIX IT UP")
                        val details=listOf("Land five slabs","Balance your first disc","Recover a lean","Build a mixed tower")
                        names.forEachIndexed { i,name ->
                            Rule();PressSurface(Modifier.fillMaxWidth().heightIn(min=88.dp).background(if(trialSelected==i)Cobalt else Sun),onClick={if(i<=prefs.trialsComplete)trialSelected=i}) {
                                Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)){Poster("0${i+1} / $name",color=if(trialSelected==i)Sun else Ink,size=24);Utility(details[i],color=if(trialSelected==i)Sun else Ink,size=11)}
                                    if(i<prefs.trialsComplete)Selection(true) else if(i>prefs.trialsComplete)Utility("LOCK",size=10)
                                }
                            }
                        };Rule();Spacer(Modifier.weight(1f));Spacer(Modifier.height(14.dp));Action("START TRIAL"){start(trialSelected)}
                    }
                    Screen.PLAY -> PlayScreen(game,frozen,navigation.last()==Screen.PLAY && foreground,navigation.last()==Screen.PLAY && !paused && !down && !won && foreground && started,paused,down,won,tutorial,trial,score,touch,
                        balance={ if(touch)game.setInput(it) },tickInput={if(!touch)game.setInput((tilt.value*(.6f+sensitivity*1.6f)).coerceIn(-1f,1f))},
                        drop={if(!down && !won && !paused){audio.interaction();if(!started)tilt.calibrate(); if(game.drop()){if(!started){if(prefs.firstPlayedAt==0L)prefs.firstPlayedAt=System.currentTimeMillis();analytics.event("tower_run_start",difficulty)};started=true;if(vibration)haptic.clink(.55f)}}},
                        frameChanged={frame ->landed(frame.score,frame.recovered,frame.holdSeconds);if(frame.down && !down){down=true;prefs.introduced=true;tutorial=false;endRun(false)}},
                        pause={paused=true;reward=null},resume={tilt.calibrate();paused=false},reset={start(trial,tutorial)},home={prefs.introduced=true;home()},settings={go(Screen.SETTINGS)},
                        toggleMusic={music=if(music>0f)0f else .7f;prefs.music=music},musicOn=music>0f,
                        useTouch={touch=true;prefs.touch=true},nextTrial={start((trial+1).coerceAtMost(3))},offer=showOffer,style={go(Screen.STYLE)},invite=config.friends && prefs.completedRuns>=runConfig.inviteAfterRuns,friends={go(Screen.RIVALS)},record=newBest)
                    Screen.SETTINGS -> Page({back()},footer={Action("DONE",Modifier.padding(top=8.dp)){back()}}) {
                        PosterFit("SETTINGS",color=Ink);Spacer(Modifier.height(15.dp))
                        SettingSlider("MUSIC",music,{music=it;prefs.music=it})
                        PressSurface(Modifier.align(Alignment.End).height(42.dp),onClick={go(Screen.MUSIC)}){Utility("SOUNDTRACK ›",Modifier.align(Alignment.Center))};Rule()
                        SettingSlider("EFFECTS",effects,{effects=it;prefs.effects=it});Rule()
                        SettingRow("HAPTICS"){Switch(vibration,"Haptics"){vibration=it;prefs.haptics=it}};Rule()
                        if(config.reminders)SettingRow("REMINDERS"){Switch(reminderOn,"Play reminders"){enabled->
                            if(enabled && !reminders.permitted())permission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            else {reminderOn=enabled;reminders.enabled=enabled}
                        }};Rule()
                        SettingRow("CONTROLS"){
                            Row(Modifier.border(1.dp,Ink)) {listOf("TILT","TOUCH").forEachIndexed {i,label -> PressSurface(Modifier.width(70.dp).height(42.dp).background(if(touch==(i==1))Ink else Sun),onClick={touch=i==1 || !tilt.available;prefs.touch=touch}){Utility(label,Modifier.align(Alignment.Center),if(touch==(i==1))Sun else Ink)} } }
                        };Rule();Spacer(Modifier.height(12.dp));Utility("SENSITIVITY");BalanceSlider(sensitivity,"Tilt sensitivity"){sensitivity=it;prefs.sensitivity=it};Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Utility("GENTLE",size=9);Utility("SHARP",size=9)};Spacer(Modifier.height(12.dp));Rule()
                        if(pack.owned || config.styleDiscovery || config.offerSettings && config.payments && pack.ready){LinkRow("STYLE PACK"){go(Screen.STYLE)};Rule()}
                        if(accountState.configured || accountState.signedIn){LinkRow(if(accountState.signedIn)"GOOGLE ACCOUNT" else "SAVE WITH GOOGLE"){go(Screen.ACCOUNT)};Rule()}
                        LinkRow("HOW TO PLAY"){onboardingReplay=true;onboardingStep=0;prefs.onboardingStep=0;prefs.onboardingBalance=0;analytics.event("tutorial_replay",difficulty);go(Screen.ONBOARDING)};Rule()
                        LinkRow("RESET LEVEL"){start(trial,tutorial)};Rule();LinkRow("PRIVACY"){context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://stack-damercy.web.app/privacy")))};Rule()
                    }
                    Screen.MUSIC -> Page({back()},footer={Action("DONE",Modifier.padding(top=8.dp)){back()}}) {
                        PosterFit("MUSIC")
                        TowerDrawing(decorativeFrame(),Modifier.fillMaxWidth().height(125.dp),decorative=true)
                        Spacer(Modifier.height(28.dp))
                        val names=listOf("MIDNIGHT SIGNAL","SIDE B","NIGHT RUN") + if(pack.owned || config.styleDiscovery)listOf("AFTER HOURS","NEON TAPE","LAST LIGHT") else emptyList()
                        val details=listOf("96 BPM · DARK ANALOGUE","112 BPM · FUNK & KEYS","120 BPM · NEON ARCADE","108 BPM · WARM SYNTH","116 BPM · BRIGHT ARPS","124 BPM · LATE ELECTRO")
                        names.forEachIndexed {i,name -> Rule();PressSurface(Modifier.fillMaxWidth().height(86.dp),onClick={if(i>=3 && !pack.owned){go(Screen.STYLE)}else {if(track==i)preview=!preview else {track=i;prefs.track=i;preview=true};autoMusic=false;prefs.autoMusic=false}}){
                            Row(Modifier.fillMaxSize(),verticalAlignment=Alignment.CenterVertically){
                                Canvas(Modifier.size(16.dp)){if(track==i)drawCircle(Vermilion,6.dp.toPx())};Utility("0${i+1}",Modifier.padding(horizontal=9.dp));Column(Modifier.weight(1f)){Poster(name,color=Ink,size=25);Utility(details[i],size=9)}
                                if(track==i && preview)GrooveWave(Modifier.padding(end=8.dp))
                                Poster(if(i>=3 && !pack.owned)"+" else if(track==i && preview)"Ⅱ" else "▶",color=Ink,size=23)
                            }
                        }};Rule();SettingRow("AUTO BY DIFFICULTY"){Switch(autoMusic,"Auto soundtrack"){autoMusic=it;prefs.autoMusic=it}};Utility("You can choose your own groove.",size=10)
                    }
                    Screen.TODAY -> Page({back()}) {
                        var time by remember{mutableStateOf(competitionTimeLabel())}
                        LaunchedEffect(foreground){while(foreground){time=competitionTimeLabel();delay(60_000)}}
                        PosterFit("TODAY");Utility(time.date,Modifier.padding(top=14.dp),size=10);Utility("DAILY BEST",Modifier.padding(vertical=16.dp));Rule()
                        Difficulty.entries.forEach {mode ->SettingRow(mode.title){Utility("${prefs.today(mode)}",size=22)};Rule()}
                        Spacer(Modifier.height(24.dp));Utility(time.reset,size=10)
                        Spacer(Modifier.weight(1f));if(config.friends)Action("FRIENDS"){go(Screen.RIVALS)};LinkRow(if(own==null)"SET USERNAME" else "@${own!!.username}"){go(Screen.PROFILE)}
                    }
                    Screen.RIVALS -> Page({back()}) {
                        PosterFit("FRIENDS",color=Ink)
                        if(own!=null)LinkRow("SHARE @${own!!.username}"){
                            val share=Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,"Stack with me. Find @${own!!.username} in Friends. https://play.google.com/store/apps/details?id=com.stackapp.stack")
                            context.startActivity(Intent.createChooser(share,"Invite a friend"))
                        } else LinkRow("SET USERNAME"){go(Screen.PROFILE)}
                        BasicTextField(query,{query=it.filter{c->c.isLetterOrDigit()||c=='_'}.take(20)},Modifier.fillMaxWidth().heightIn(min=56.dp).padding(vertical=14.dp).semantics{contentDescription="Search usernames"}.testTag("username_search"),singleLine=true,textStyle=TextStyle(fontFamily=UtilityFont,fontSize=18.sp,color=Ink),cursorBrush=SolidColor(Vermilion),decorationBox={inner->if(query.isBlank())Utility("FIND A FRIEND");inner()});Rule()
                        Row(Modifier.fillMaxWidth().padding(vertical=12.dp),horizontalArrangement=Arrangement.SpaceBetween){Utility("${difficulty.title} · BEST",size=10);Utility("YOU ${maxOf(prefs.best(difficulty),own?.score(difficulty)?:0)}",size=10)}
                        Row(Modifier.fillMaxWidth().border(1.dp,Ink)){Difficulty.entries.forEach{mode->PressSurface(Modifier.weight(1f).height(44.dp).background(if(mode==difficulty)Ink else Sun),"Compare ${mode.title}",onClick={difficulty=mode;prefs.difficulty=mode}){Utility(mode.label,Modifier.align(Alignment.Center),if(mode==difficulty)Sun else Ink,size=10)}}}
                        if(!social.available)Utility("Friends unavailable. Try later.",Modifier.padding(vertical=16.dp))
                        if(loadingSocial || searching)Utility("LOADING…",Modifier.padding(vertical=12.dp),size=10)
                        val error=if(query.length>=2)searchError else socialError
                        if(error.isNotBlank()){Utility(error,color=Vermilion);LinkRow("RETRY"){refresh++}}
                        val shown=(if(query.trim().length>=2)results else people.sortedWith(compareByDescending<Competitor>{it.score(difficulty)}.thenBy{usernameKey(it.username)})).filter{it.id!=own?.id}
                        if(shown.isEmpty() && !loadingSocial && !searching && social.available && error.isBlank())Utility(if(query.length>=2)"No players found." else "Search a username to add a friend.",Modifier.padding(vertical=22.dp))
                        shown.forEach{player->
                            Row(Modifier.fillMaxWidth().heightIn(min=88.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Utility("@${player.username}",size=15);Utility("${player.score(difficulty,true)} TODAY",size=9)}
                                Utility("${player.score(difficulty)}",Modifier.padding(horizontal=12.dp),size=20)
                                PressSurface(Modifier.size(64.dp,48.dp),if(player.id in followed)"Unfollow ${player.username}" else "Follow ${player.username}",onClick={if(player.id in followed)rivals.unfollow(player.id) else rivals.follow(player.id);followed=rivals.ids()}){Utility(if(player.id in followed)"SAVED" else "+ ADD",Modifier.align(Alignment.Center),size=10)}
                            };Rule()
                        }
                        Spacer(Modifier.weight(1f));LinkRow("REFRESH"){refresh++};Action("PLAY"){start()}
                    }
                    Screen.PROFILE -> Page({back()}) {
                        PosterFit("USERNAME",color=Ink)
                        if(accountState.configured && !accountState.signedIn)LinkRow("SAVE WITH GOOGLE"){go(Screen.ACCOUNT)}
                        Spacer(Modifier.height(28.dp));Utility("3–20 LETTERS, NUMBERS OR _",size=10)
                        BasicTextField(displayName,{displayName=it.filter {c->c in 'A'..'Z'||c in 'a'..'z'||c in '0'..'9'||c=='_'}.take(20);profileError=""},Modifier.fillMaxWidth().padding(vertical=18.dp).semantics{contentDescription="Your username"}.testTag("username_entry"),singleLine=true,textStyle=TextStyle(fontFamily=UtilityFont,fontSize=24.sp,color=Ink),cursorBrush=SolidColor(Vermilion),decorationBox={inner->if(displayName.isBlank())Utility("USERNAME",color=Ink.copy(alpha=.35f),size=24);inner()});Rule()
                        Utility(profileError,Modifier.heightIn(min=32.dp),Vermilion);Utility("Your username and scores are public.",size=10);Spacer(Modifier.weight(1f));Action(if(profileBusy)"SAVING…" else "SAVE"){
                            if(!profileBusy)when {
                                !validUsername(displayName)->profileError="Use 3–20 letters, numbers or _."
                                !social.available->profileError="Connect to reserve your username."
                                else->{profileBusy=true;scope.launch{try{own=social.claim(displayName);saveName(displayName);refresh++;if(navigation.last()==Screen.PROFILE)back()}
                                    catch(cancelled:CancellationException){throw cancelled}
                    catch(error:Exception){profileError=if(error is NameUnavailable || generateSequence(error.cause){it.cause}.any{it is NameUnavailable})"Username taken." else "Couldn’t save. Retry."}
                                    finally{profileBusy=false}}}
                            }
                        };LinkRow("LATER"){back()}
                        if(own!=null) {
                            if(!confirmDelete)LinkRow("DELETE PROFILE"){confirmDelete=true}
                            else {Utility("Remove your public username and scores?",size=11,color=Vermilion)
                                LinkRow("DELETE"){if(!profileBusy){profileBusy=true;scope.launch{try{social.remove();own=null;displayName="";saveName("");confirmDelete=false;refresh++;if(navigation.last()==Screen.PROFILE)back()}catch(_:Exception){profileError="Couldn’t delete. Retry."}finally{profileBusy=false}}}}
                                LinkRow("CANCEL"){confirmDelete=false}
                            }
                        }
                    }
                    Screen.STYLE -> Page({back()},footer={
                        if(pack.message.isNotBlank())Utility(pack.message,Modifier.padding(vertical=10.dp),size=10)
                        if(pack.owned)Action("DONE"){back()}
                        else if(config.payments && config.verificationReady && pack.ready && !pack.busy && !pack.pending)Action("UNLOCK ${pack.price}"){analytics.event("style_purchase_started",difficulty,score);(context as? android.app.Activity)?.let{store.buy(it,config)}}
                        else if(pack.message.isBlank())Utility(if(pack.busy)"CHECKING PURCHASE..." else "PACK COMING SOON",Modifier.padding(vertical=16.dp),size=10)
                    }) {
                        PosterFit("YOUR STYLE",color=Ink);Utility("THREE THEMES. THREE NEW GROOVES.",Modifier.padding(vertical=16.dp),size=10)
                        BalanceSkin.entries.forEach{choice->
                            Rule();PressSurface(Modifier.fillMaxWidth().height(130.dp),"Theme ${choice.title}",onClick={if(choice==BalanceSkin.GOLD || pack.owned){skin=choice;prefs.skin=choice}}){
                                CompositionLocalProvider(LocalBalancePalette provides choice.palette){
                                    Row(Modifier.fillMaxSize().background(choice.palette.background).padding(12.dp),verticalAlignment=Alignment.CenterVertically){
                                        Column(Modifier.weight(1f)){Poster(choice.title,color=choice.palette.ink,size=34);Utility(if(skin==choice && (pack.owned || choice==BalanceSkin.GOLD))"SELECTED" else if(choice==BalanceSkin.GOLD)"FREE" else if(pack.owned)"USE THEME" else "STYLE PACK",color=choice.palette.ink,size=9)}
                                        TowerDrawing(decorativeFrame(),Modifier.width(130.dp).fillMaxHeight(),decorative=true)
                                    }
                                }
                            }
                        };Rule();Utility("AFTER HOURS / NEON TAPE / LAST LIGHT",Modifier.padding(vertical=16.dp),size=10)
                        Utility("Same physics. Same scores. Yours to keep.",size=10)
                        LinkRow("RESTORE PURCHASES"){store.restore()}
                        if(!pack.ready && !pack.owned)LinkRow("RETRY"){scope.launch{store.sync(config)}}
                    }
                    Screen.COUNTRY -> Page({back()}) {
                        PosterFit("COUNTRY",color=Ink);var search by remember {mutableStateOf("")}
                        BasicTextField(search,{search=it},Modifier.fillMaxWidth().padding(vertical=22.dp),singleLine=true,textStyle=TextStyle(fontFamily=UtilityFont,fontSize=18.sp,color=Ink),decorationBox={inner->if(search.isBlank())Utility("SEARCH");inner()});Rule()
                        val countries=remember {java.util.Locale.getISOCountries().map {it to java.util.Locale.Builder().setRegion(it).build().getDisplayCountry(java.util.Locale.ENGLISH)}.sortedBy{it.second}}
                        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())){countries.filter {it.second.contains(search,true)||it.first.equals(search,true)}.forEach {(code,name)->PressSurface(Modifier.fillMaxWidth().background(if(country==code)Cobalt else Sun).height(56.dp),onClick={country=code}){Utility(name.uppercase(),Modifier.align(Alignment.CenterStart).padding(10.dp),if(country==code)Sun else Ink)} }}
                        Spacer(Modifier.height(12.dp));Action("DONE"){saveCountry(country);back()}
                    }
                }
            } })
        }
        }
    }
    // Gameplay Back opens its pause state; other destinations keep NavDisplay's predictive motion.
    }
    if(current==Screen.PLAY && !paused && foreground)reward?.let{item->CompositionLocalProvider(LocalBalancePalette provides palette){RewardBurst(item,runConfig.celebrationMillis){if(reward?.id==item.id)reward=null}}}
    BackHandler(current==Screen.PLAY || current==Screen.ONBOARDING) { back() }
}
@Composable private fun Page(close:()->Unit, footer:@Composable ()->Unit={}, content:@Composable ColumnScope.()->Unit) {
    val palette=LocalBalancePalette.current
    val Sun=palette.background;val Cobalt=palette.primary;val Vermilion=palette.accent;val Ink=palette.ink;val Cream=palette.paper
    BoxWithConstraints(Modifier.fillMaxSize().background(Sun)) {
        val wide=maxWidth>=650.dp
        val availableWidth=maxWidth
        Row(Modifier.fillMaxSize().padding(horizontal=if(wide)40.dp else 20.dp,vertical=8.dp),horizontalArrangement=Arrangement.spacedBy(40.dp)) {
            if(wide)Column(Modifier.weight(1f).fillMaxHeight(),verticalArrangement=Arrangement.Center){PosterFit("OFF");PosterFit("BALANCE",color=Ink);TowerDrawing(decorativeFrame(),Modifier.fillMaxWidth().height(240.dp),decorative=true)}
            Column(Modifier.weight(1f).fillMaxHeight().widthIn(max=520.dp)) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){Utility("OFF BALANCE",color=Ink,size=9);Symbol("Back",onClick=close)}
                BoxWithConstraints(Modifier.weight(1f)) {
                    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min=maxHeight),content=content)
                }
                footer()
            }
        }
    }
}
@Composable private fun SettingRow(label:String,content:@Composable ()->Unit){Row(Modifier.fillMaxWidth().heightIn(min=60.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){Utility(label);content()}}
@Composable private fun SettingSlider(label:String,value:Float,changed:(Float)->Unit){Spacer(Modifier.height(14.dp));Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Utility(label);Utility("${(value*100).toInt()}%")};BalanceSlider(value,label,changed)}
@Composable fun LinkRow(label:String,onClick:()->Unit){PressSurface(Modifier.fillMaxWidth().heightIn(min=56.dp),onClick=onClick){Row(Modifier.fillMaxWidth().align(Alignment.Center),horizontalArrangement=Arrangement.SpaceBetween){Utility(label);Utility("›",size=22)}}}
@Composable private fun Selection(selected:Boolean){
    val palette=LocalBalancePalette.current
    val Sun=palette.background;val Cobalt=palette.primary;val Vermilion=palette.accent;val Ink=palette.ink;val Cream=palette.paper
Box(Modifier.size(28.dp).then(if(selected)Modifier.background(Vermilion) else Modifier.border(1.5.dp,Ink)),contentAlignment=Alignment.Center){if(selected)Utility("✓",color=Cream,size=20)}}
private fun decorativeFrame(variant:Int=1):BalanceFrame {
    val beam=PiecePose(0f,0f,-.07f,3.4f,.28f,PieceKind.SLAB,1)
    return BalanceFrame(listOf(PiecePose(.05f,.45f,-.12f,1.5f,.5f,PieceKind.SLAB,0),PiecePose(-.98f,.59f,.08f,.78f,.78f,PieceKind.DISC,2),PiecePose(.85f,.92f,-.30f,1.3f,.5f,if(variant==2)PieceKind.WEDGE else PieceKind.SLAB,1)),beam,null,0,0f,false,false,false,0f)
}
@Composable private fun HomeScreen(difficulty:Difficulty,best:Int,settings:()->Unit,modes:()->Unit,play:()->Unit,trials:()->Unit,today:()->Unit,trialsEnabled:Boolean=true) {
    val palette=LocalBalancePalette.current
    val Sun=palette.background;val Cobalt=palette.primary;val Vermilion=palette.accent;val Ink=palette.ink;val Cream=palette.paper
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal=20.dp,vertical=8.dp)) {
        val wide=maxWidth>=650.dp
        val availableWidth=maxWidth
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Utility("OFF BALANCE",color=Ink,size=9);Symbol("Settings",onClick=settings)}
            if(wide)Row(Modifier.weight(1f).fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(50.dp)) {
                Column(Modifier.weight(1f)){PosterFit("STACK");Utility("TAP. TILT. RECOVER.",Modifier.fillMaxWidth(),align=TextAlign.Center);TowerDrawing(decorativeFrame(),Modifier.fillMaxWidth().height(240.dp),decorative=true)}
                Column(Modifier.weight(1f)){HomeActions(difficulty,best,modes,play,trials,today,trialsEnabled)}
            } else {
                BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                    val headlineHeight=minOf(maxWidth,maxHeight*.62f)
                    var headlinePixels by remember { mutableIntStateOf(1) }
                    val headlineTarget=with(androidx.compose.ui.platform.LocalDensity.current){headlineHeight.toPx()}
                    Column(Modifier.fillMaxSize(),horizontalAlignment=Alignment.CenterHorizontally) {
                        Box(Modifier.fillMaxWidth().height(headlineHeight)) {
                            PosterFit("STACK",Modifier.onSizeChanged{headlinePixels=it.height}.graphicsLayer {scaleY=headlineTarget/headlinePixels.coerceAtLeast(1);transformOrigin=TransformOrigin(.5f,0f)})
                        }
                        Utility("TAP. TILT. RECOVER.",Modifier.fillMaxWidth(),align=TextAlign.Center)
                        TowerDrawing(decorativeFrame(),Modifier.fillMaxWidth().weight(1f),decorative=true)
                    }
                }
                HomeActions(difficulty,best,modes,play,trials,today,trialsEnabled)
            }
        }
    }
}
@Composable private fun HomeActions(difficulty:Difficulty,best:Int,modes:()->Unit,play:()->Unit,trials:()->Unit,today:()->Unit,trialsEnabled:Boolean){
    Utility("BEST $best · ${difficulty.title}",Modifier.fillMaxWidth(),align=TextAlign.Center);Spacer(Modifier.height(6.dp));PressSurface(Modifier.fillMaxWidth().height(48.dp),onClick=modes){Utility("${difficulty.title} / ${difficulty.label} ⌄",Modifier.align(Alignment.Center))};Action("PLAY",onClick=play);Spacer(Modifier.height(10.dp));Rule();Row(Modifier.fillMaxWidth()){if(trialsEnabled)PressSurface(Modifier.weight(1f).height(52.dp),onClick=trials){Utility("TRIALS",Modifier.align(Alignment.Center))};PressSurface(Modifier.weight(1f).height(52.dp),onClick=today){Utility("TODAY",Modifier.align(Alignment.Center))}}
}
@Composable private fun PlayScreen(game:BalancePhysics,frameState:MutableState<BalanceFrame>,visible:Boolean,running:Boolean,paused:Boolean,down:Boolean,won:Boolean,tutorial:Boolean,trial:Int,score:Int,touch:Boolean,balance:(Float)->Unit,tickInput:()->Unit,drop:()->Unit,frameChanged:(BalanceFrame)->Unit,pause:()->Unit,resume:()->Unit,reset:()->Unit,home:()->Unit,settings:()->Unit,toggleMusic:()->Unit,musicOn:Boolean,useTouch:()->Unit,nextTrial:()->Unit,offer:Boolean=false,style:()->Unit={},invite:Boolean=false,friends:()->Unit={},record:Boolean=false) {
    val palette=LocalBalancePalette.current
    val Sun=palette.background;val Cobalt=palette.primary;val Vermilion=palette.accent;val Ink=palette.ink;val Cream=palette.paper
    var lean by remember(game){mutableFloatStateOf(0f)}
    var recovered by remember(game){mutableStateOf(false)}
    var hold by remember(game){mutableIntStateOf(0)}
    val input by rememberUpdatedState(tickInput)
    val changed by rememberUpdatedState(frameChanged)
    LaunchedEffect(game,running,down,paused,visible) {
        if(!visible || !running && (!down || paused))return@LaunchedEffect
        var last=withFrameNanos{it}
        var next=last+16_666_667L
        while(isActive){
            val now=withFrameNanos{it}
            if(now+500_000L<next)continue
            next+=16_666_667L;if(next<now)next=now+16_666_667L
            if(down && game.collapseFinished)break
            input()
            Trace.beginSection("Balance.Physics")
            val frame=try { game.advance((now-last)/1e9) } finally { Trace.endSection() };last=now
            frameState.value=frame
            // Only coarse HUD changes recompose; the drawing reads the frame itself.
            val nextLean=(frame.lean*25).toInt()/25f;if(nextLean!=lean)lean=nextLean
            recovered=frame.recovered;hold=frame.holdSeconds.toInt();changed(frame)
        }
    }
    val latestDrop by rememberUpdatedState(drop)
    BoxWithConstraints(Modifier.fillMaxSize().background(Sun).pointerInput(paused,down,won){
        detectTapGestures{if(!paused && !down && !won && frameState.value.canDrop)latestDrop()}
    }.testTag("game_surface").padding(horizontal=20.dp,vertical=8.dp)) {
        val wide=maxWidth>=650.dp
        val availableWidth=maxWidth
        val headline=when {down->"DOWN.";won->"NICE.";trial==1->"ROUND";trial>=0->"HOLD";abs(lean)>.26f->"EASY";recovered->"HOLD";else->"STACK"}
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().semantics{contentDescription="Run: $score layers, ${when{down->"over";won->"complete";paused->"paused";else->"playing"}}"}.testTag("run_status"),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){Utility("OFF BALANCE",color=Ink,size=9);Utility(if(trial>=0)"TRIAL 0${trial+1}" else if(score>0)layerLabel(score) else "");Symbol(if(paused||down||won)"Close" else "Pause",onClick=if(down||won)home else if(paused)resume else pause)}
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if(wide)Row(Modifier.fillMaxSize(),verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(.9f).fillMaxHeight().padding(end=24.dp)) {
                        MovingPoster(if(paused)"PAUSE" else headline,size=130)
                        Utility(if(paused)"" else if(down)"" else instruction(score,touch,trial,lean,recovered),Modifier.fillMaxWidth(),align=TextAlign.Center)
                        Spacer(Modifier.weight(1f))
                        if(!paused && !down && !won)ThumbControls(lean,touch,balance)
                        RunActions(paused,down,won,trial,hold,score,touch,tutorial,resume,reset,home,toggleMusic,musicOn,settings,useTouch,nextTrial,offer,style,invite,friends,record)
                    }
                    key(game){PlayCanvas(frameState,Modifier.weight(1.1f).fillMaxHeight(),paused,drop,won)}
                } else {
                    MovingPoster(if(paused)"PAUSE" else headline,Modifier.align(Alignment.TopCenter),size=(availableWidth.value*.45f).toInt())
                    key(game){PlayCanvas(frameState,Modifier.fillMaxSize().padding(top=availableWidth*.45f+8.dp,bottom=if(paused||down||won)100.dp else 74.dp),paused,drop,won)}
                    if(!paused && !down && !won)Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally){Utility(instruction(score,touch,trial,lean,recovered),align=TextAlign.Center,size=10);ThumbControls(lean,touch,balance)}
                }
            }
            if(!wide)RunActions(paused,down,won,trial,hold,score,touch,tutorial,resume,reset,home,toggleMusic,musicOn,settings,useTouch,nextTrial,offer,style,invite,friends,record)
        }
    }
}
@Composable private fun PlayCanvas(frame:State<BalanceFrame>,modifier:Modifier,paused:Boolean,drop:()->Unit,complete:Boolean){
    // This small scope alone observes the 60 Hz frame, keeping navigation and text idle.
    PressSurface(modifier.testTag("game_scene"),if(complete)"Completed tower" else "Drop the next piece",{if(!paused && !complete && frame.value.canDrop)drop()},enabled=!paused && !complete,pressFeedback=false){key(frame){TowerDrawing({if(complete)frame.value.copy(incoming=null) else frame.value},Modifier.fillMaxSize(),if(paused).17f else 1f)}}
}
@Composable private fun ThumbControls(lean:Float,touch:Boolean,balance:(Float)->Unit){
    Box(Modifier.fillMaxWidth().height(98.dp),contentAlignment=Alignment.Center){
        BalanceGauge(lean,Modifier.widthIn(max=260.dp).testTag("balance_control"),touch,balance)
    }
}
private fun instruction(score:Int,touch:Boolean,trial:Int,lean:Float,recovered:Boolean)=when {
    trial==0->"LAND FIVE SLABS."
    trial==1->"LAND THE DISC. HOLD FOR 3 SECONDS."
    trial==2->"RECOVER A LEAN."
    trial==3->"BUILD EIGHT LAYERS."
    score==0->if(touch)"Tap to begin." else "Hold comfortably. Tap to begin."
    abs(lean)>.26f->if(lean>0)"A LITTLE LEFT." else "A LITTLE RIGHT."
    recovered->"NICE SAVE."
    else->"TAP TO LAND. FIND YOUR BALANCE."
}

@Composable private fun RunActions(paused:Boolean,down:Boolean,won:Boolean,trial:Int,hold:Int,score:Int,touch:Boolean,tutorial:Boolean,resume:()->Unit,reset:()->Unit,home:()->Unit,toggleMusic:()->Unit,musicOn:Boolean,settings:()->Unit,useTouch:()->Unit,nextTrial:()->Unit,offer:Boolean=false,style:()->Unit={},invite:Boolean=false,friends:()->Unit={},record:Boolean=false) {
            AnimatedContent(when{paused->1;down->2;won->3;else->0},transitionSpec={(fadeIn(tween(130))+slideInVertically(spring(.75f,600f)){it/3}) togetherWith (fadeOut(tween(90))+slideOutVertically(tween(130)){it/3})},label="Run actions") { mode ->
                Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    when(mode) {
                        1->{Action("RESUME",onClick=resume);Action("RESET LEVEL",outline=true,onClick=reset);Action("END RUN",outline=true,onClick=home);Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){PressSurface(Modifier.height(48.dp),onClick=toggleMusic){Utility(if(musicOn)"MUSIC ON" else "MUSIC OFF",Modifier.align(Alignment.Center))};PressSurface(Modifier.height(48.dp),onClick=settings){Utility("SETTINGS",Modifier.align(Alignment.Center))}}}
                        2->{if(record)Utility("NEW BEST",Modifier.fillMaxWidth(),align=TextAlign.Center);Utility(layerLabel(score),Modifier.fillMaxWidth(),size=24,align=TextAlign.Center);Action("PLAY AGAIN",onClick=reset);if(offer)LinkRow("STYLE PACK",style);if(invite)LinkRow("FRIENDS",friends);LinkRow("HOME",home)}
                        3->{Utility(if(trial<0)"TOWER COMPLETE." else if(trial==3)"ALL FOUR. NICE WORK." else "TRIAL COMPLETE",Modifier.fillMaxWidth(),align=TextAlign.Center);Action(if(trial<0 || trial==3)"PLAY AGAIN" else "NEXT TRIAL",onClick=if(trial<0 || trial==3)reset else nextTrial);if(offer)LinkRow("STYLE PACK",style);if(invite)LinkRow("FRIENDS",friends);LinkRow("HOME",home)}
                        else->{if(trial==1)Utility("${hold.coerceAtMost(3)} / 3 SEC",Modifier.fillMaxWidth(),align=TextAlign.Center)
                            if(score==0 && tutorial && !touch)PressSurface(Modifier.fillMaxWidth().height(48.dp),onClick=useTouch){Utility("USE TOUCH INSTEAD",Modifier.align(Alignment.Center),size=10)}
                            else Utility(if(touch)"DRAG TO BALANCE" else "TILT TO BALANCE",Modifier.fillMaxWidth().padding(vertical=14.dp),size=9,align=TextAlign.Center)}
                    }
                }
            }
}
private fun layerLabel(score:Int)="$score ${if(score==1)"LAYER" else "LAYERS"}"
