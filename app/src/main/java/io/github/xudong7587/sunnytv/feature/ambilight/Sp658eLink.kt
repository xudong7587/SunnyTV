@file:Suppress("DEPRECATION")
package io.github.xudong7587.sunnytv.feature.ambilight

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.Handler
import android.os.SystemClock
import java.util.UUID

/** All mutable state is confined to the supplied worker. At most one BLE write is in flight. */
@SuppressLint("MissingPermission")
internal class Sp658eLink(private val context:Context,private val worker:Handler,
    private val preferredAddress:String,private val status:(String)->Unit,
    private val ready:(String)->Unit,private val sent:(Long)->Unit,private val failed:()->Unit) {
    private var gatt:BluetoothGatt?=null
    private var characteristic:BluetoothGattCharacteristic?=null
    private var scanner:BluetoothLeScanner?=null
    private var closed=false
    private var found=false
    private var configured=false
    private var ticket=0
    private val decoder=Sp658eStateDecoder()
    private var pending:Target?=null
    private var active:Target?=null
    private val writes=java.util.ArrayDeque<ByteArray>()
    private var writing=false
    private var mode=-1
    private var level=-1
    private var began=0L
    private var stateTicket=0
    private data class Target(val rgb:Int?,val slots:ByteArray?,val brightness:Int,val created:Long=SystemClock.elapsedRealtime())
    private fun uuid(short:String)=UUID.fromString("0000$short-0000-1000-8000-00805f9b34fb")
    private fun current(g:BluetoothGatt)=!closed && g===gatt
    private fun fail(message:String) {if(closed)return;android.util.Log.w("SunnyAmbilight",message);status(message);close();failed()}

    fun start() {
        try {
            val adapter=(context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
            if(adapter==null || !adapter.isEnabled) {fail("请开启电视蓝牙");return}
            scanner=adapter.bluetoothLeScanner
            if(scanner==null) {fail("电视不支持低功耗蓝牙扫描");return}
            status("正在查找 SP658E…")
            scanner!!.startScan(null,ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),scan)
            worker.postDelayed({if(!closed && !configured) fail("连接超时，请确认灯带通电、手机 BanLanX 已退出")},22000)
        } catch(_:SecurityException) {fail("请在灯带设置中授权蓝牙")}
        catch(_:Exception) {fail("蓝牙连接启动失败，请重试")}
    }
    private val scan=object:ScanCallback() {
        override fun onScanResult(type:Int,result:ScanResult) {worker.post {
            if(closed || found)return@post
            val record=result.scanRecord?:return@post
            val manufacturer=record.getManufacturerSpecificData(0x5053)?:return@post
            if(record.deviceName!="SP658E" || manufacturer.size<2 || manufacturer[0]!=0x3f.toByte() || manufacturer[1]!=0x10.toByte())return@post
            if(preferredAddress.isNotBlank() && result.device.address!=preferredAddress)return@post
            found=true
            try {scanner?.stopScan(this);status("正在连接 SP658E…");gatt=result.device.connectGatt(context,false,callback,BluetoothDevice.TRANSPORT_LE)}
            catch(_:Exception) {fail("无法连接灯带，请重试")}
        }}
        override fun onScanFailed(code:Int) {worker.post {fail("蓝牙扫描失败（$code），请稍后重试")}}
    }
    private val callback=object:BluetoothGattCallback() {
        override fun onConnectionStateChange(g:BluetoothGatt,s:Int,newState:Int) {worker.post {
            if(!current(g))return@post
            if(s==0 && newState==BluetoothProfile.STATE_CONNECTED) {
                if(!g.discoverServices())fail("无法读取灯带服务")
            } else if(newState==BluetoothProfile.STATE_DISCONNECTED || s!=0) fail("灯带连接断开（$s）")
        }}
        override fun onServicesDiscovered(g:BluetoothGatt,s:Int) {worker.post {
            if(!current(g))return@post
            val service=g.getService(uuid("ffe0"))?:g.getService(uuid("e0ff"))
            val c=service?.getCharacteristic(uuid("ffe1"));characteristic=c
            val d=c?.getDescriptor(uuid("2902"))
            if(s!=0 || c==null || d==null || !g.setCharacteristicNotification(c,true)) {fail("没有找到 SP658E 控制服务");return@post}
            d.value=BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            if(!g.writeDescriptor(d))fail("无法订阅灯带状态")
        }}
        override fun onDescriptorWrite(g:BluetoothGatt,d:BluetoothGattDescriptor,s:Int) {worker.post {
            if(!current(g))return@post
            if(s!=0 || !g.requestMtu(185))fail("蓝牙数据长度协商失败")
        }}
        override fun onMtuChanged(g:BluetoothGatt,mtu:Int,s:Int) {worker.post {
            if(!current(g))return@post
            if(s!=0 || mtu<185) {fail("蓝牙数据长度不足，无法读取灯带状态");return@post}
            writes.add(Sp658eProtocol.query);drain()
        }}
        override fun onCharacteristicWrite(g:BluetoothGatt,c:BluetoothGattCharacteristic,s:Int) {worker.post {
            if(!current(g) || !writing)return@post
            writing=false;ticket++
            if(s!=0) {fail("灯带写入失败（$s）");return@post}
            // Callback means accepted by the Bluetooth stack, not that the LEDs have changed.
            worker.postDelayed({if(!closed)drain()},8)
        }}
        override fun onCharacteristicChanged(g:BluetoothGatt,c:BluetoothGattCharacteristic) {
            val packet=c.value?.clone()?:return
            worker.post {if(current(g))readState(packet)}
        }
    }
    private fun readState(packet:ByteArray) {
        val bytes=decoder.accept(packet,SystemClock.elapsedRealtime())?:return
        if(bytes.size<82)return
        stateTicket++
        if(configured)return
        if(String(bytes,11,8,Charsets.US_ASCII)!="V4023.21" || bytes[19]!=6.toByte()) {fail("此版本仅验证 SP658E / V4023.21 / SPI RGB");return}
        if(bytes[29]!=1.toByte()) {fail("请先开启灯带电源，再连接");return}
        configured=true;status("SP658E 已连接")
        ready(gatt!!.device.address);drain();scheduleHealthCheck()
    }
    private fun scheduleHealthCheck() {
        worker.postDelayed({
            if(closed || !configured)return@postDelayed
            // A stack write callback alone cannot prove the remote controller is still alive.
            val serial=stateTicket
            writes.add(Sp658eProtocol.query);drain()
            worker.postDelayed({if(!closed && stateTicket==serial)fail("灯带状态响应超时，正在重新连接")},6000)
            scheduleHealthCheck()
        },15_000)
    }
    fun fixed(rgb:Int,brightness:Int) {if(!closed){pending=Target(LightOutputLimit.fixed(rgb,brightness),null,brightness);drain()}}
    fun custom(slots:ByteArray,brightness:Int) {if(!closed){pending=Target(null,LightOutputLimit.slots(slots,brightness),brightness);drain()}}
    private fun drain() {
        if(closed || writing)return
        if(writes.isEmpty() && active!=null) {
            val done=active!!;active=null;mode=if(done.rgb!=null)1 else 7;level=done.brightness
            sent(SystemClock.elapsedRealtime()-done.created)
        }
        if(writes.isEmpty() && configured) {
            val next=pending?:return;pending=null;active=next;began=SystemClock.elapsedRealtime()
            writes.addAll(Sp658eProtocol.update(next.rgb,next.slots,next.brightness,mode,level))
        }
        if(writes.isEmpty())return
        val c=characteristic?:return
        c.writeType=BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE;c.value=writes.removeFirst();writing=true
        if(gatt?.writeCharacteristic(c)!=true) {writing=false;fail("蓝牙写入失败，请重新连接");return}
        val serial=++ticket
        worker.postDelayed({if(!closed && writing && ticket==serial)fail("蓝牙写入超时，请重新连接")},1800)
    }
    fun close() {
        if(closed)return;closed=true;pending=null;writes.clear();active=null
        runCatching {scanner?.stopScan(scan)}
        runCatching {gatt?.disconnect();gatt?.close()};gatt=null
    }
}
