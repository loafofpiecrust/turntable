package com.loafofpiecrust.turntable.service

import android.content.Context
import android.util.SparseArray
import com.evgenii.jsevaluator.JsEvaluator
import com.evgenii.jsevaluator.interfaces.JsCallback
import com.loafofpiecrust.turntable.tryOr
import com.loafofpiecrust.turntable.util.Http
import com.loafofpiecrust.turntable.util.text
import com.loafofpiecrust.turntable.youtube.YtFile
import com.loafofpiecrust.turntable.youtube.YtFormat
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async
import java.io.*
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.regex.Pattern
import kotlin.coroutines.experimental.suspendCoroutine


/**
 * Created by snead on 8/16/17.
 */
class YouTubeDash(val context: Context) {
    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 6.1; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/40.0.2214.115 Safari/537.36"
        private const val USE_HTTP = false
        private const val CACHE_FILE_NAME = "decipher_js_funct"

        private const val STREAM_MAP_STRING = "url_encoded_fmt_stream_map"

        private val patDashManifest1 = Pattern.compile("dashmpd=(.+?)(&|\\z)")
        private val patDashManifest2 = Pattern.compile("\"dashmpd\":\"(.+?)\"")
        private val patDashManifestEncSig = Pattern.compile("/s/([0-9A-F|.]{10,}?)(/|\\z)")
        private val patDecryptionJsFile = Pattern.compile("jsbin\\\\/(player-(.+?).js)")
        private val patSignatureDecFunction = Pattern.compile("\\(\"signature\",(.{1,3}?)\\(.{1,10}?\\)")
        private val patVariableFunction = Pattern.compile("([{; =])([a-zA-Z$][a-zA-Z0-9$]{0,2})\\.([a-zA-Z$][a-zA-Z0-9$]{0,2})\\(")
        private val patFunction = Pattern.compile("([{; =])([a-zA-Z\$_][a-zA-Z0-9$]{0,2})\\(")

        private val patIsSigEnc = Pattern.compile("s%3D([0-9A-F|.]{10,}?)%26")
        private val patEncSig = Pattern.compile("s=([0-9A-F|.]{10,}?)([&,\"])")
        private val patItag = Pattern.compile("itag=([0-9]+?)([&,])")
        private val patUrl = Pattern.compile("url=(.+?)([&,])")
    }


    private var decipherJsFileName: String? = null
    private var decipherFunctions: String? = null
    private var decipherFunctionName: String? = null

    suspend fun getDashManifest(videoId: String): String? {
        val infoUrl = (if (USE_HTTP) "http://" else "https://") +
            "www.youtube.com/get_video_info?video_id=" + videoId +
            "&eurl=" +
            URLEncoder.encode("https://youtube.googleapis.com/v/$videoId", "UTF-8")
//        val conn = infoUrl.openConnection() as HttpURLConnection
//        val reader = BufferedReader(InputStreamReader(conn.inputStream))
//
//        // grab the stream map
//        var streamMap = reader.readLine()
//
//        // close the connection
//        reader.close()
//        conn.disconnect()
        val res = tryOr(null) {
            Http.get(infoUrl, params = mapOf(), headers = mapOf(
                "User-Agent" to USER_AGENT
            )).text
        }
        var streamMap = res?.lineSequence()?.firstOrNull()

        val signatures = SparseArray<String>()

//        parseMeta(streamMap)

        var sigEnc = true
        if (streamMap != null && streamMap.contains(STREAM_MAP_STRING)) {
            val streamMapSub = streamMap.substring(streamMap.indexOf(STREAM_MAP_STRING))
            val mat = patIsSigEnc.matcher(streamMapSub)
            if (!mat.find())
                sigEnc = false
        }


        val dashUrl = if (!sigEnc) {
            async(UI) { println("youtube: no cipher") }
            // This is a normal video
            val mat = patDashManifest1.matcher(streamMap)
            streamMap = URLDecoder.decode(streamMap, "UTF-8")
            if (mat.find()) {
                URLDecoder.decode(mat.group(1), "UTF-8").also {
                    async(UI) { println("youtube: got a uuid? '$it'") }
                }
            } else null
        } else {
            async(UI) { println("youtube: encrypted!") }
            // This is encrypted or something, use JS to get around it.

            if (decipherJsFileName == null ||
                decipherFunctions == null ||
                decipherFunctionName == null) {
                readDecipherFuncFromCache()
            }

            val res = Http.get("https://youtube.com/watch?v=$videoId", headers = mapOf(
                "User-Agent" to USER_AGENT
            )).text

            async(UI) { println("youtube: before '$streamMap'") }
            // wtf?
            val line = res.lineSequence().find { it.contains(STREAM_MAP_STRING) }
            if (line != null) {
                streamMap = line.replace("\\u0026", "&")
            }
            async(UI) { println("youtube: after '$streamMap'") }

            // Find JS file to decrypt everything

            var mat = patDecryptionJsFile.matcher(streamMap)
            if (mat.find()) {
                async(UI) { println("youtube: found js decryption file?") }
                // Replace windows path-separators with Unix ones
                val curJsFileName = mat.group(1).replace("\\/", "/")
                if (decipherJsFileName == null || decipherJsFileName != curJsFileName) {
                    decipherFunctions = null
                    decipherFunctionName = null
                }
                decipherJsFileName = curJsFileName
            }

            // Find dash manifest uuid here
            mat = patDashManifest2.matcher(streamMap)
            if (mat.find()) {
                val dashUrl = mat.group(1).replace("\\/", "/")
                mat = patDashManifestEncSig.matcher(dashUrl)
                async(UI) { println("youtube: got dash uuid? '$dashUrl'") }
                if (mat.find()) {
                    signatures.append(0, mat.group(1))
                    dashUrl
                } else {
                    // no dash?
                    async(UI) { println("youtube: turns out maybe not.") }
                    null
                }
            } else {
                async(UI) { println("youtube: no dash manifest up in here.") }
                null
            }
        }

        val streams = streamMap!!.split(",|$STREAM_MAP_STRING|&adaptive_fmts=")
        val ytFiles = SparseArray<YtFile>()
        for (encStream in streams) {
            if (!encStream.contains("itag%3D")) {
                continue
            }
            val encStream = "$encStream,"
            val stream = URLDecoder.decode(encStream, "UTF-8")
            var mat = patItag.matcher(stream)
            val itag = if (mat.find()) {
                mat.group(1).toInt()
            } else 0

            if (decipherJsFileName != null) {
                mat = patEncSig.matcher(stream)
                if (mat.find()) {
                    signatures.append(itag, mat.group(1))
                }
            }
            mat = patUrl.matcher(encStream)
            if (mat.find()) {
                val url = mat.group(1)
                val finalUrl = URLDecoder.decode(url, "UTF-8")
                val vid = com.loafofpiecrust.turntable.youtube.YtFile(YtFormat.Audio(itag, "mp4", YtFormat.ACodec.AAC, 128), finalUrl)
                ytFiles.put(itag, vid)
            }
        }

        return if (signatures.size() > 0 && dashUrl != null) {
            async(UI) { println("youtube: deciphering signatures") }
            // get the cipher via JS to decipher the uuid signatures (or something...)
            val deciphered = decipherSignature(signatures)
            if (deciphered == null) {
                // nothing, didn't work
                async(UI) { println("youtube: deciphering failed") }
                null
            } else {
                async(UI) { println("youtube: success!!!") }
                val sigs = deciphered.split("\n")
//                val end = minOf(signatures.size(), sigs.size)
                val idx = signatures.indexOfKey(0)
                dashUrl.replace(
                    "/s/${signatures.get(0)}",
                    "/signature/${sigs[idx]}"
                )
            }
        } else dashUrl
    }

    private fun readDecipherFuncFromCache() {
//        val context = App.instance
//        if (context != null) {
            val cacheFile = File(context.cacheDir.absolutePath + "/" + "decipher_js_funct")
            if (cacheFile.exists() && System.currentTimeMillis() - cacheFile.lastModified() < 1209600000L) {
                var reader: BufferedReader? = null

                try {
                    reader = BufferedReader(InputStreamReader(FileInputStream(cacheFile), "UTF-8"))
                    decipherJsFileName = reader.readLine()
                    decipherFunctionName = reader.readLine()
                    decipherFunctions = reader.readLine()
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    if (reader != null) {
                        try {
                            reader.close()
                        } catch (e: IOException) {
                            e.printStackTrace()
                        }
                    }
                }
            }
//        }

    }


    @Throws(IOException::class)
    private suspend fun decipherSignature(signatures: SparseArray<String>): String? {
        // Assume the functions don't change that much
        if (decipherFunctionName == null || decipherFunctions == null) {
            val decipherFuncUrl = "https://s.ytimg.com/yts/jsbin/$decipherJsFileName"

//            var reader: BufferedReader? = null
            val javascriptFile = Http.get(decipherFuncUrl, params = mapOf(), headers = mapOf(
                "User-Agent" to USER_AGENT
            )).text.lineSequence().joinToString(" ")

            println("youtube Decipher FunctURL: $decipherFuncUrl")

            var mat = patSignatureDecFunction.matcher(javascriptFile)
            if (mat.find()) {
                decipherFunctionName = mat.group(1)
                println("youtube Decipher Functname: $decipherFunctionName")

                val patMainVariable = Pattern.compile("(var |\\s|,|;)${decipherFunctionName!!.replace("$", "\\$")}(=function\\((.{1,3})\\)\\{)")

                var mainDecipherFunct: String

                mat = patMainVariable.matcher(javascriptFile)
                if (mat.find()) {
                    mainDecipherFunct = "var " + decipherFunctionName + mat.group(2)
                } else {
                    val patMainFunction = Pattern.compile("function ${decipherFunctionName!!.replace("$", "\\$")}(\\((.{1,3})\\)\\{)")
                    mat = patMainFunction.matcher(javascriptFile)
                    if (!mat.find())
                        return null
                    mainDecipherFunct = "function " + decipherFunctionName + mat.group(2)
                }

                var startIndex = mat.end()

                var braces = 1
                for (i in startIndex until javascriptFile.length) {
                    if (braces == 0 && startIndex + 5 < i) {
                        mainDecipherFunct += javascriptFile.substring(startIndex, i) + ";"
                        break
                    }
                    if (javascriptFile[i] == '{')
                        braces++
                    else if (javascriptFile[i] == '}')
                        braces--
                }

                decipherFunctions = mainDecipherFunct
                // Search the main function for extra functions and variables
                // needed for deciphering
                // Search for variables
                mat = patVariableFunction.matcher(mainDecipherFunct)
                while (mat.find()) {
                    val variableDef = "var " + mat.group(2) + "={"
                    if (decipherFunctions!!.contains(variableDef)) {
                        continue
                    }
                    startIndex = javascriptFile.indexOf(variableDef) + variableDef.length
                    var braces = 1
                    for (i in startIndex until javascriptFile.length) {
                        if (braces == 0) {
                            decipherFunctions += variableDef + javascriptFile.substring(startIndex, i) + ";"
                            break
                        }
                        if (javascriptFile[i] == '{')
                            braces++
                        else if (javascriptFile[i] == '}')
                            braces--
                    }
                }

                // Search for functions
                mat = patFunction.matcher(mainDecipherFunct)
                while (mat.find()) {
                    val functionDef = "function " + mat.group(2) + "("
                    if (decipherFunctions!!.contains(functionDef)) {
                        continue
                    }
                    startIndex = javascriptFile.indexOf(functionDef) + functionDef.length
                    var braces = 0
                    for (i in startIndex until javascriptFile.length) {
                        if (braces == 0 && startIndex + 5 < i) {
                            decipherFunctions += functionDef + javascriptFile.substring(startIndex, i) + ";"
                            break
                        }
                        if (javascriptFile[i] == '{')
                            braces++
                        else if (javascriptFile[i] == '}')
                            braces--
                    }
                }

                println("Decipher Function: $decipherFunctions")
//                async(CommonPool) {
//                }
//                if (CACHING) {
                    writeDeciperFuncToCache()
//                }
                return decipherViaWebView(signatures)
            } else {
                return null
            }
        } else {
            return decipherViaWebView(signatures)
        }
//        return true
    }

    private fun writeDeciperFuncToCache() {
        val cacheFile = context.cacheDir.resolve(CACHE_FILE_NAME)
        val writer = cacheFile.bufferedWriter()
        writer.write(decipherJsFileName + "\n")
        writer.write(decipherFunctionName + "\n")
        writer.write(decipherFunctions)
        writer.flush()
        writer.close()
    }

    private suspend fun decipherViaWebView(signatures: SparseArray<String>): String = suspendCoroutine { cont ->
        val stb = StringBuilder("$decipherFunctions function decipher(){return ")
        (0 until signatures.size()).map { it to signatures.keyAt(it) }.forEach { (idx, key) ->
            stb.append("$decipherFunctionName('${signatures.get(key)}')")
            if (idx < signatures.size() - 1) {
                stb.append("+\"\\n\"+")
            }
        }
        stb.append("};decipher();")

        // Apparently should happen on the UI thread? Maybe since this uses WebView (?)
        async(UI) {
            val js = JsEvaluator(this@YouTubeDash.context)
            js.evaluate(stb.toString(), object: JsCallback {
                override fun onResult(p0: String?) {
                    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
                }

                override fun onError(p0: String?) {
                    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
                }
            })
        }
    }
}