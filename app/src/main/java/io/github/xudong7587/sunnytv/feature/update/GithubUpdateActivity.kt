package io.github.xudong7587.sunnytv.feature.update

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import io.github.xudong7587.sunnytv.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import java.io.File

/** Native updater: does not depend on a browser/WebView to discover or download a release. */
class GithubUpdateActivity:ComponentActivity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        val layout=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setPadding(24,20,24,20)}
        fun label(value:String)=TextView(this).apply {text=value;textSize=18f;setPadding(0,12,0,12);layout.addView(this)}
        fun button(value:String,action:()->Unit)=Button(this).apply {text=value;setOnClickListener {action()};layout.addView(this)}
        button("返回 SunnyTV") {finish()}
        label("GitHub 官方更新")
        label("当前版本：${BuildConfig.VERSION_NAME}（${BuildConfig.VERSION_CODE}）")
        val status=label("正在检查最新发行…")
        var release:GithubUpdates.Release?=null
        var ready:File?=null
        var job:Job?=null
        var downloading=false
        lateinit var check:Button
        lateinit var download:Button
        lateinit var install:Button
        val checkLatest:()->Unit={
            job?.cancel();release=null;ready=null;download.isEnabled=false;install.isEnabled=false
            check.isEnabled=false;status.text="正在检查最新发行…"
            job=lifecycleScope.launch {
                try {
                    val found=GithubUpdates.latest();release=found
                    download.isEnabled=found.versionCode>BuildConfig.VERSION_CODE
                    status.text="官方最新版本：${found.versionName}（${found.versionCode}）\n"+
                        if(download.isEnabled) "发现新版本，可下载升级。" else if(found.versionCode==BuildConfig.VERSION_CODE.toLong()) "当前已是最新版本。" else "当前安装版本高于官方发行版本。"
                } catch(_:TimeoutCancellationException) {status.text="检查超时。请重试，或返回设置使用 WebDAV 更新。"}
                catch(e:CancellationException) {throw e}
                catch(_:Exception) {status.text="无法检查最新版本。请检查 GitHub 网络连接后重试，也可返回设置使用 WebDAV 更新。"}
                finally {check.isEnabled=true}
            }
        }
        check=button("重新检查最新版本") {checkLatest()}
        download=button("下载最新版本") {
            val selected=release
            if(selected!=null && !downloading) {
                ready=null;downloading=true;download.isEnabled=false;check.isEnabled=false;install.isEnabled=false
                status.text="正在下载 ${selected.versionName} 并校验升级包…"
                job=lifecycleScope.launch {
                    try {ready=GithubUpdates.download(this@GithubUpdateActivity,selected.apk);install.isEnabled=true;status.text="${selected.versionName} 下载与签名校验通过，请点击安装。"}
                    catch(e:CancellationException) {status.text="下载已取消";throw e}
                    catch(_:Exception) {status.text="下载失败。请检查 GitHub 网络连接后重试，或返回设置使用 WebDAV 更新。"}
                    finally {downloading=false;check.isEnabled=true;download.isEnabled=true}
                }
            }
        }.apply {isEnabled=false}
        button("取消下载") {if(downloading) job?.cancel()}
        install=button("安装已下载更新") {ready?.let {file->
            try {ExperimentalUpdater(this).install(file);status.text="若刚授予安装权限，请返回后再次点击安装。"}
            catch(_:Exception) {status.text="无法打开系统安装器"}
        }}.apply {isEnabled=false}
        button("浏览官方发行页面") {
            try {startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(GithubUpdates.LATEST_PAGE)))}
            catch(_:Exception) {status.text="设备没有可用浏览器。可直接在本页检查和下载更新。"}
        }
        setContentView(ScrollView(this).apply {addView(layout)})
        checkLatest()
    }
}
