package io.github.xudong7587.sunnytv.feature.ambilight

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Process-wide single owner of the controller; playback and calibration never open competing GATTs. */
class AmbilightController(context:Context) {
    private val context=context.applicationContext
    private val prefs=this.context.getSharedPreferences("ambilight",Context.MODE_PRIVATE)
    private val worker=Handler(HandlerThread("sunnytv-lights").apply {start()}.looper)
    private val _config=MutableStateFlow(readConfig())
    val config=_config.asStateFlow()
    private val _status=MutableStateFlow("灯带未连接")
    val status=_status.asStateFlow()
    private val _connected=MutableStateFlow(false)
    val connected=_connected.asStateFlow()
    private val _metrics=MutableStateFlow("尚未开始同步")
    val metrics=_metrics.asStateFlow()
    private var link:Sp658eLink?=null
    private var settingsOpen=false
    private var playerOwner:Any?=null
    private var preview=false
    private var retries=0
    private var cleanup=0
    private var lastColors:IntArray?=null
    private var lastFrameAt=0L
    private var metricAt=0L
    private var sentCount=0
    private var totalSendMs=0L
    private var captureMs=0L
    private fun readConfig()=LightConfig(prefs.getBoolean("enabled",false),prefs.getBoolean("sync",true),
        prefs.getInt("fixed",0xff4d05) and 0xffffff,prefs.getInt("brightness",128).coerceIn(1,255),
        prefs.getInt("fps",10).takeIf {it in listOf(5,10,15)}?:10,
        LightLayout(prefs.getInt("count",96),prefs.getInt("tl",49),prefs.getInt("tr",18),prefs.getInt("br",3),prefs.getInt("bl",65))
            .takeIf {it.direction()!=null}?:LightLayout(),prefs.getString("device","").orEmpty())

    fun permissions()=if(Build.VERSION.SDK_INT>=31) arrayOf(Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_CONNECT)
        else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    fun hasPermissions()=permissions().all {context.checkSelfPermission(it)==PackageManager.PERMISSION_GRANTED}
    fun save(next:LightConfig) {
        require(next.layout.direction()!=null && next.brightness in 1..255 && next.fps in listOf(5,10,15))
        // Validate the worst-case, four different colors before saving an unrepresentable layout.
        Sp658eProtocol.edges(next.layout,intArrayOf(0xff0000,0x00ff00,0x0000ff,0xffff00))
        _config.value=next
        prefs.edit().putBoolean("enabled",next.enabled).putBoolean("sync",next.sync).putInt("fixed",next.fixedColor)
            .putInt("brightness",next.brightness).putInt("fps",next.fps).putInt("count",next.layout.count)
            .putInt("tl",next.layout.topLeft).putInt("tr",next.layout.topRight).putInt("br",next.layout.bottomRight)
            .putInt("bl",next.layout.bottomLeft).putString("device",next.deviceAddress).apply()
        worker.post {
            lastColors=null
            if(!next.enabled) {restore();scheduleClose(force=true)}
            else {ensureConnected();if(!preview)restore()}
        }
    }
    fun enterSettings() {worker.post {settingsOpen=true;cleanup++;retries=0;if(config.value.enabled)ensureConnected()}}
    fun leaveSettings() {worker.post {settingsOpen=false;preview=false;restore();scheduleClose()}}
    fun connect(forget:Boolean=false) {
        if(forget)save(config.value.copy(deviceAddress=""))
        worker.post {cleanup++;retries=0;link?.close();link=null;_connected.value=false;ensureConnected()}
    }
    fun disconnect() {worker.post {retries=2;_connected.value=false;restore();scheduleClose(force=true)}}
    private fun wanted()=config.value.enabled && (settingsOpen || playerOwner!=null)
    private fun ensureConnected() {
        if(link!=null || !wanted())return
        if(!hasPermissions()) {_status.value="请在灯带设置中授权蓝牙";return}
        cleanup++
        val next=Sp658eLink(context,worker,config.value.deviceAddress,{_status.value=it},{address->
            _connected.value=true;retries=0
            val c=config.value
            if(c.deviceAddress!=address) {
                _config.value=c.copy(deviceAddress=address);prefs.edit().putString("device",address).apply()
            }
            restore()
        },{elapsed->
            sentCount++;totalSendMs+=elapsed
            val now=SystemClock.elapsedRealtime()
            if(now-metricAt>=1000) {
                if(metricAt>0)_metrics.value="发送 %.1f 次/秒 · 取色 %d ms · 蓝牙提交 %d ms".format(
                    sentCount*1000.0/(now-metricAt),captureMs,totalSendMs/sentCount.coerceAtLeast(1))
                android.util.Log.i("SunnyAmbilight",_metrics.value)
                metricAt=now;sentCount=0;totalSendMs=0
            }
        },{
            link=null;_connected.value=false;lastColors=null
            if(wanted() && retries<2) {retries++;worker.postDelayed({ensureConnected()},1500)}
        })
        link=next;next.start()
    }
    fun startPlayback(owner:Any) {worker.post {
        playerOwner=owner;preview=false;cleanup++;retries=0;lastColors=null
        if(config.value.enabled)ensureConnected()
    }}
    fun stopPlayback(owner:Any) {worker.post {
        if(playerOwner!==owner)return@post
        playerOwner=null;lastColors=null;restore();scheduleClose()
    }}
    fun frame(owner:Any,colors:IntArray,costMs:Long) {worker.post {
        val c=config.value
        if(playerOwner!==owner || preview || !c.enabled || !c.sync || !_connected.value)return@post
        captureMs=costMs;lastFrameAt=SystemClock.elapsedRealtime()
        if(lastColors?.contentEquals(colors)==true)return@post
        try {link?.custom(Sp658eProtocol.edges(c.layout,colors),c.brightness);lastColors=colors.clone()}
        catch(_:IllegalArgumentException) {_status.value="定位超出七段限制，请重新设定四角";restore()}
    }}
    fun idle(owner:Any,reason:String="暂停或等待视频，已恢复固定色") {worker.post {
        if(playerOwner===owner && !preview && lastColors!=null) {lastColors=null;restore();_metrics.value=reason}
    }}
    fun note(message:String) {_metrics.value=message}
    fun marker(count:Int,address:Int) {worker.post {
        if(!_connected.value) {_status.value="请先连接灯带";return@post}
        preview=true;lastColors=null
        runCatching {link?.custom(Sp658eProtocol.marker(count,address),64)}.onFailure {_status.value="地址超出分段范围"}
    }}
    fun testEdges(layout:LightLayout=config.value.layout) {worker.post {
        if(!_connected.value) {_status.value="请先连接灯带";return@post}
        preview=true;lastColors=null
        runCatching {link?.custom(Sp658eProtocol.edges(layout,intArrayOf(0xff0000,0x00ff00,0x0000ff,0xffff00)),64)}
            .onFailure {_status.value=it.message?:"四角定位无效"}
    }}
    fun fixed() {worker.post {preview=false;lastColors=null;restore()}}
    fun previewColor(rgb:Int) {worker.post {
        preview=true;lastColors=null;link?.fixed(rgb,config.value.brightness)
    }}
    private fun restore() {val c=config.value;link?.fixed(c.fixedColor,c.brightness)}
    private fun scheduleClose(force:Boolean=false) {
        if(!force && wanted())return
        val serial=++cleanup
        worker.postDelayed({if(serial==cleanup && (force || !wanted())) {
            link?.close();link=null;_connected.value=false;_status.value="灯带已断开，保留固定色"
        }},2200)
    }
}
