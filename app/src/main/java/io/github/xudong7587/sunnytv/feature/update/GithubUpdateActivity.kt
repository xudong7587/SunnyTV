package io.github.xudong7587.sunnytv.feature.update

import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.File

/** User-requested official release browser; no JS bridge or app credentials. */
class GithubUpdateActivity:ComponentActivity() {
    private var web:WebView?=null
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        val layout=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL}
        val status=TextView(this).apply {text="请选择官方发行的 APK，下载后校验签名并由系统确认安装。";setPadding(20,12,20,12)}
        var ready:File?=null
        val install=Button(this).apply {text="安装已下载更新";isEnabled=false;setOnClickListener {ready?.let {file->
            try {ExperimentalUpdater(this@GithubUpdateActivity).install(file);status.text="若刚授予安装权限，请返回后再次点击安装。"}
            catch(_:Exception) {status.text="无法打开系统安装器"}
        }}}
        layout.addView(Button(this).apply {text="返回 SunnyTV";setOnClickListener {finish()}})
        layout.addView(status);layout.addView(install)
        var downloading=false
        val download:(String)->Unit={url->
            if(!downloading) lifecycleScope.launch {
                downloading=true;install.isEnabled=false;status.text="正在下载并校验官方升级包…"
                try {ready=GithubUpdates.download(this@GithubUpdateActivity,url);install.isEnabled=true;status.text="下载与签名校验通过，请点击安装。"}
                catch(e:CancellationException) {throw e}
                catch(_:Exception) {status.text="下载失败：请检查网络、发行校验值及版本是否匹配。"}
                finally {downloading=false}
            }
        }
        val browser=WebView(this).apply {
            settings.javaScriptEnabled=true
            settings.allowFileAccess=false;settings.allowContentAccess=false
            settings.domStorageEnabled=false;settings.setSupportMultipleWindows(false)
            settings.mixedContentMode=android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            webViewClient=object:WebViewClient() {
                override fun shouldOverrideUrlLoading(view:WebView,request:WebResourceRequest):Boolean {
                    val uri=request.url
                    if(uri.scheme=="https" && uri.host=="github.com" && uri.path.orEmpty().startsWith("/xudong7587/SunnyTV/releases/download/") && uri.path.orEmpty().endsWith(".apk")) {
                        download(uri.toString());return true
                    }
                    return !GithubUpdates.allowedPage(uri.toString())
                }
            }
            setDownloadListener {url,_,_,_,_->download(url)}
            loadUrl(GithubUpdates.PAGE)
        }
        web=browser;layout.addView(browser,LinearLayout.LayoutParams(-1,0,1f));setContentView(layout)
    }
    override fun onDestroy() {web?.stopLoading();web?.destroy();web=null;super.onDestroy()}
}
