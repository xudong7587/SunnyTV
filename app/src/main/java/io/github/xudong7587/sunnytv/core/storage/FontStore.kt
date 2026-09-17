package io.github.xudong7587.sunnytv.core.storage

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.util.UUID

/** Copies only the selected document into app-private storage; no broad storage permission. */
object FontStore {
    const val MAX_BYTES=64L*1024*1024
    fun import(context:Context,uri:Uri):Pair<String,String> {
        val resolver=context.contentResolver
        val name=resolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use {cursor->
            if(cursor.moveToFirst()) cursor.getString(0) else null
        } ?: "自定义字体"
        val directory=File(context.filesDir,"fonts").apply {mkdirs()}
        val file=File(directory,"${UUID.randomUUID()}.font")
        try {
            resolver.openInputStream(uri)?.use {input->file.outputStream().use {output->
                val buffer=ByteArray(64*1024)
                var total=0L
                while(true) {
                    val count=input.read(buffer)
                    if(count<0) break
                    total+=count
                    require(total<=MAX_BYTES) {"字体文件不能超过 64 MB"}
                    output.write(buffer,0,count)
                }
                require(total>0) {"字体文件为空"}
            }} ?: error("无法打开字体文件")
            val header=file.inputStream().use {it.readNBytesCompat(4)}
            require(header.contentEquals(byteArrayOf(0,1,0,0)) || String(header,Charsets.US_ASCII) in setOf("OTTO","ttcf","true")) {
                "请选择 TTF、OTF 或 TTC 字体文件"
            }
            Typeface.createFromFile(file) // Reject corrupt fonts before changing the saved preference.
            return file.name to name.take(120)
        } catch(e:Exception) {
            file.delete()
            throw e
        }
    }
    private fun java.io.InputStream.readNBytesCompat(count:Int):ByteArray {
        val bytes=ByteArray(count)
        var read=0
        while(read<count) {val n=read(bytes,read,count-read);if(n<0) break;read+=n}
        return bytes.copyOf(read)
    }
}
