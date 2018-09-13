//package com.loafofpiecrust.turntable.youtube
//
//import android.content.Context
//import android.os.AsyncTask
//import android.os.Handler
//import android.os.Looper
//import android.util.Log
//import android.util.SparseArray
//import at.huber.youtubeExtractor.Format
//import at.huber.youtubeExtractor.VideoMeta
//import at.huber.youtubeExtractor.YtFile
//
//import com.evgenii.jsevaluator.JsEvaluator
//import com.evgenii.jsevaluator.interfaces.JsCallback
//
//import java.io.BufferedReader
//import java.io.BufferedWriter
//import java.io.File
//import java.io.FileInputStream
//import java.io.FileOutputStream
//import java.io.IOException
//import java.io.InputStreamReader
//import java.io.OutputStreamWriter
//import java.io.UnsupportedEncodingException
//import java.net.HttpURLConnection
//import java.net.URL
//import java.net.URLDecoder
//import java.net.URLEncoder
//import java.util.concurrent.TimeUnit
//import java.util.concurrent.locks.ReentrantLock
//import java.util.regex.Matcher
//import java.util.regex.Pattern
//
//abstract class YouTubeExtractor(private val context: Context?) : AsyncTask<String, Void, SparseArray<YtFile>>() {
//    private var videoID: String? = null
//    private var videoMeta: VideoMeta? = null
//    private var includeWebM = true
//    private var useHttp = false
//    private var parseDashManifest = false
//
//    @Volatile
//    private var decipheredSignature: String? = null
//
//    private val lock = ReentrantLock()
//    private val jsExecuting = lock.newCondition()
//
//    private// "use_cipher_signature" disappeared, we check whether at least one ciphered signature
//        // exists int the stream_map.
//        // Some videos are using a ciphered signature we need to get the
//        // deciphering js-file from the youtubepage.
//        // Get the video directly from the youtubepage
//        // Log.d("line", line);
//        // It sometimes fails to connect for no apparent reason. We just retry.
//    val streamUrls: SparseArray<YtFile>?
//        @Throws(IOException::class, InterruptedException::class)
//        get() {
//
//            var ytInfoUrl = if (useHttp) "http://" else "https://"
//            ytInfoUrl += ("www.youtube.com/get_video_info?video_id=" + videoID + "&eurl="
//                + URLEncoder.encode("https://youtube.googleapis.com/v/" + videoID!!, "UTF-8"))
//
//            var dashMpdUrl: String? = null
//            var streamMap: String?
//            var reader: BufferedReader? = null
//            var getUrl = URL(ytInfoUrl)
//            if (LOGGING)
//                Log.d(LOG_TAG, "infoUrl: " + ytInfoUrl)
//            var urlConnection = getUrl.openConnection() as HttpURLConnection
//            urlConnection.setRequestProperty("User-Agent", USER_AGENT)
//            try {
//                reader = BufferedReader(InputStreamReader(urlConnection.inputStream))
//                streamMap = reader.readLine()
//
//            } finally {
//                if (reader != null)
//                    reader.close()
//                urlConnection.disconnect()
//            }
//            var mat: Matcher
//            var curJsFileName: String? = null
//            var streams: Array<String>
//            var encSignatures: SparseArray<String>? = null
//
//            parseVideoMeta(streamMap)
//
//            if (videoMeta!!.isLiveStream) {
//                mat = patHlsvp.matcher(streamMap!!)
//                if (mat.find()) {
//                    val hlsvp = URLDecoder.decode(mat.group(1), "UTF-8")
//                    val ytFiles = SparseArray<YtFile>()
//
//                    getUrl = URL(hlsvp)
//                    urlConnection = getUrl.openConnection() as HttpURLConnection
//                    urlConnection.setRequestProperty("User-Agent", USER_AGENT)
//                    try {
//                        reader = BufferedReader(InputStreamReader(urlConnection.inputStream))
//                        reader.lineSequence().forEach { line ->
//                            if (line.startsWith("https://") || line.startsWith("http://")) {
//                                mat = patHlsItag.matcher(line)
//                                if (mat.find()) {
//                                    val itag = Integer.parseInt(mat.group(1))
//                                    val newFile = YtFile(FORMAT_MAP.get(itag), line)
//                                    ytFiles.put(itag, newFile)
//                                }
//                            }
//                        }
//                    } finally {
//                        if (reader != null)
//                            reader.close()
//                        urlConnection.disconnect()
//                    }
//
//                    if (ytFiles.size() == 0) {
//                        if (LOGGING)
//                            Log.d(LOG_TAG, streamMap)
//                        return null
//                    }
//                    return ytFiles
//                }
//                return null
//            }
//            var sigEnc = true
//            if (streamMap != null && streamMap.contains(STREAM_MAP_STRING)) {
//                val streamMapSub = streamMap.substring(streamMap.indexOf(STREAM_MAP_STRING))
//                mat = patIsSigEnc.matcher(streamMapSub)
//                if (!mat.find())
//                    sigEnc = false
//            }
//            if (sigEnc) {
//                if (CACHING && (decipherJsFileName == null || decipherFunctions == null || decipherFunctionName == null)) {
//                    readDecipherFunctFromCache()
//                }
//                getUrl = URL("https://youtube.com/watch?v=" + videoID!!)
//                urlConnection = getUrl.openConnection() as HttpURLConnection
//                urlConnection.setRequestProperty("User-Agent", USER_AGENT)
//                try {
//                    reader = BufferedReader(InputStreamReader(urlConnection.inputStream))
//                    val line = reader.lineSequence().first { it.contains(STREAM_MAP_STRING) }
//                    streamMap = line.replace("\\u0026", "&")
//                } finally {
//                    if (reader != null)
//                        reader.close()
//                    urlConnection.disconnect()
//                }
//                encSignatures = SparseArray()
//
//                mat = patDecryptionJsFile.matcher(streamMap!!)
//                if (mat.find()) {
//                    curJsFileName = mat.group(1).replace("\\/", "/")
//                    if (decipherJsFileName == null || decipherJsFileName != curJsFileName) {
//                        decipherFunctions = null
//                        decipherFunctionName = null
//                    }
//                    decipherJsFileName = curJsFileName
//                }
//
//                if (parseDashManifest) {
//                    mat = patDashManifest2.matcher(streamMap)
//                    if (mat.find()) {
//                        dashMpdUrl = mat.group(1).replace("\\/", "/")
//                        mat = patDashManifestEncSig.matcher(dashMpdUrl)
//                        if (mat.find()) {
//                            encSignatures.append(0, mat.group(1))
//                        } else {
//                            dashMpdUrl = null
//                        }
//                    }
//                }
//            } else {
//                if (parseDashManifest) {
//                    mat = patDashManifest1.matcher(streamMap!!)
//                    if (mat.find()) {
//                        dashMpdUrl = URLDecoder.decode(mat.group(1), "UTF-8")
//                    }
//                }
//                streamMap = URLDecoder.decode(streamMap!!, "UTF-8")
//            }
//
//            streams = streamMap!!.split(",|${STREAM_MAP_STRING}|&adaptive_fmts=".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
//            val ytFiles = SparseArray<YtFile>()
//            for (encStream in streams) {
//                if (!encStream.contains("itag%3D")) {
//                    continue
//                }
//                val encStream = encStream + ","
//                val stream: String
//                stream = URLDecoder.decode(encStream, "UTF-8")
//
//                mat = patItag.matcher(stream)
//                val itag: Int
//                if (mat.find()) {
//                    itag = Integer.parseInt(mat.group(1))
//                    if (LOGGING)
//                        Log.d(LOG_TAG, "Itag found:" + itag)
//                    if (FORMAT_MAP.get(itag) == null) {
//                        if (LOGGING)
//                            Log.d(LOG_TAG, "Itag not in list:" + itag)
//                        continue
//                    } else if (!includeWebM && FORMAT_MAP.get(itag).ext == "webm") {
//                        continue
//                    }
//                } else {
//                    continue
//                }
//
//                if (curJsFileName != null) {
//                    mat = patEncSig.matcher(stream)
//                    if (mat.find()) {
//                        encSignatures!!.append(itag, mat.group(1))
//                    }
//                }
//                mat = patUrl.matcher(encStream)
//                var url: String? = null
//                if (mat.find()) {
//                    url = mat.group(1)
//                }
//
//                if (url != null) {
//                    val format = FORMAT_MAP.get(itag)
//                    val finalUrl = URLDecoder.decode(url, "UTF-8")
//                    val newVideo = YtFile(format, finalUrl)
//                    ytFiles.put(itag, newVideo)
//                }
//            }
//
//            if (encSignatures != null) {
//                if (LOGGING)
//                    Log.d(LOG_TAG, "Decipher signatures")
//                val signature: String?
//                decipheredSignature = null
//                if (decipherSignature(encSignatures)) {
//                    lock.lock()
//                    try {
//                        jsExecuting.await(7, TimeUnit.SECONDS)
//                    } finally {
//                        lock.unlock()
//                    }
//                }
//                signature = decipheredSignature
//                if (signature == null) {
//                    return null
//                } else {
//                    val sigs = signature.split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
//                    var i = 0
//                    while (i < encSignatures.size() && i < sigs.size) {
//                        val key = encSignatures.keyAt(i)
//                        if (key == 0) {
//                            dashMpdUrl = dashMpdUrl!!.replace("/s/" + encSignatures.get(key), "/signature/" + sigs[i])
//                        } else {
//                            var url = ytFiles.get(key).url
//                            url += "&signature=" + sigs[i]
//                            val newFile = YtFile(FORMAT_MAP.get(key), url)
//                            ytFiles.put(key, newFile)
//                        }
//                        i++
//                    }
//                }
//            }
//
//            if (parseDashManifest && dashMpdUrl != null) {
//                for (i in 0 until DASH_PARSE_RETRIES) {
//                    try {
//                        parseDashManifest(dashMpdUrl, ytFiles)
//                        break
//                    } catch (io: IOException) {
//                        Thread.sleep(5)
//                        if (LOGGING)
//                            Log.d(LOG_TAG, "Failed to parse dash manifest " + (i + 1))
//                    }
//
//                }
//            }
//
//            if (ytFiles.size() == 0) {
//                if (LOGGING)
//                    Log.d(LOG_TAG, streamMap)
//                return null
//            }
//            return ytFiles
//        }
//
//    override fun onPostExecute(ytFiles: SparseArray<YtFile>?) {
//        onExtractionComplete(ytFiles, videoMeta)
//    }
//
//
//    /**
//     * Start the extraction.
//     *
//     * @param youtubeLink       the youtube page link or video id
//     * @param parseDashManifest true if the dash manifest should be downloaded and parsed
//     * @param includeWebM       true if WebM streams should be extracted
//     */
//    fun extract(youtubeLink: String, parseDashManifest: Boolean, includeWebM: Boolean) {
//        this.parseDashManifest = parseDashManifest
//        this.includeWebM = includeWebM
//        this.execute(youtubeLink)
//    }
//
//    protected abstract fun onExtractionComplete(ytFiles: SparseArray<YtFile>?, videoMeta: VideoMeta?)
//
//    override fun doInBackground(vararg params: String): SparseArray<YtFile>? {
//        videoID = null
//        val ytUrl = params[0] ?: return null
//        var mat = patYouTubePageLink.matcher(ytUrl)
//        if (mat.find()) {
//            videoID = mat.group(3)
//        } else {
//            mat = patYouTubeShortLink.matcher(ytUrl)
//            if (mat.find()) {
//                videoID = mat.group(3)
//            } else if (ytUrl.matches("\\p{Graph}+?".toRegex())) {
//                videoID = ytUrl
//            }
//        }
//        if (videoID != null) {
//            try {
//                return streamUrls
//            } catch (e: Exception) {
//                e.printStackTrace()
//            }
//
//        } else {
//            Log.e(LOG_TAG, "Wrong YouTube link format")
//        }
//        return null
//    }
//
//    @Throws(IOException::class)
//    private fun decipherSignature(encSignatures: SparseArray<String>): Boolean {
//        // Assume the functions don't change that much
//        if (decipherFunctionName == null || decipherFunctions == null) {
//            val decipherFunctUrl = "https://s.ytimg.com/yts/jsbin/" + decipherJsFileName!!
//
//            var reader: BufferedReader? = null
//            val javascriptFile: String
//            val url = URL(decipherFunctUrl)
//            val urlConnection = url.openConnection() as HttpURLConnection
//            urlConnection.setRequestProperty("User-Agent", USER_AGENT)
//            try {
//                reader = BufferedReader(InputStreamReader(urlConnection.inputStream))
//                val sb = StringBuilder("")
//                var line = reader.readLine()
//                while (line != null) {
//                    sb.append(line)
//                    sb.append(" ")
//                    line = reader.readLine()
//                }
//                javascriptFile = sb.toString()
//            } finally {
//                if (reader != null)
//                    reader.close()
//                urlConnection.disconnect()
//            }
//
//            if (LOGGING)
//                Log.d(LOG_TAG, "Decipher FunctURL: " + decipherFunctUrl)
//            var mat = patSignatureDecFunction.matcher(javascriptFile)
//            if (mat.find()) {
//                decipherFunctionName = mat.group(1)
//                if (LOGGING)
//                    Log.d(LOG_TAG, "Decipher Functname: " + decipherFunctionName!!)
//
//                val patMainVariable = Pattern.compile("(var |\\s|,|;)" + decipherFunctionName!!.replace("$", "\\$") +
//                    "(=function\\((.{1,3})\\)\\{)")
//
//                var mainDecipherFunct: String
//
//                mat = patMainVariable.matcher(javascriptFile)
//                if (mat.find()) {
//                    mainDecipherFunct = "var " + decipherFunctionName + mat.group(2)
//                } else {
//                    val patMainFunction = Pattern.compile("function " + decipherFunctionName!!.replace("$", "\\$") +
//                        "(\\((.{1,3})\\)\\{)")
//                    mat = patMainFunction.matcher(javascriptFile)
//                    if (!mat.find())
//                        return false
//                    mainDecipherFunct = "function " + decipherFunctionName + mat.group(2)
//                }
//
//                var startIndex = mat.end()
//
//                run {
//                    var braces = 1
//                    var i = startIndex
//                    while (i < javascriptFile.length) {
//                        if (braces == 0 && startIndex + 5 < i) {
//                            mainDecipherFunct += javascriptFile.substring(startIndex, i) + ";"
//                            break
//                        }
//                        if (javascriptFile[i] == '{')
//                            braces++
//                        else if (javascriptFile[i] == '}')
//                            braces--
//                        i++
//                    }
//                }
//                decipherFunctions = mainDecipherFunct
//                // Search the main function for extra functions and variables
//                // needed for deciphering
//                // Search for variables
//                mat = patVariableFunction.matcher(mainDecipherFunct)
//                while (mat.find()) {
//                    val variableDef = "var " + mat.group(2) + "={"
//                    if (decipherFunctions!!.contains(variableDef)) {
//                        continue
//                    }
//                    startIndex = javascriptFile.indexOf(variableDef) + variableDef.length
//                    var braces = 1
//                    var i = startIndex
//                    while (i < javascriptFile.length) {
//                        if (braces == 0) {
//                            decipherFunctions += variableDef + javascriptFile.substring(startIndex, i) + ";"
//                            break
//                        }
//                        if (javascriptFile[i] == '{')
//                            braces++
//                        else if (javascriptFile[i] == '}')
//                            braces--
//                        i++
//                    }
//                }
//                // Search for functions
//                mat = patFunction.matcher(mainDecipherFunct)
//                while (mat.find()) {
//                    val functionDef = "function " + mat.group(2) + "("
//                    if (decipherFunctions!!.contains(functionDef)) {
//                        continue
//                    }
//                    startIndex = javascriptFile.indexOf(functionDef) + functionDef.length
//                    var braces = 0
//                    var i = startIndex
//                    while (i < javascriptFile.length) {
//                        if (braces == 0 && startIndex + 5 < i) {
//                            decipherFunctions += functionDef + javascriptFile.substring(startIndex, i) + ";"
//                            break
//                        }
//                        if (javascriptFile[i] == '{')
//                            braces++
//                        else if (javascriptFile[i] == '}')
//                            braces--
//                        i++
//                    }
//                }
//
//                if (LOGGING)
//                    Log.d(LOG_TAG, "Decipher Function: " + decipherFunctions!!)
//                decipherViaWebView(encSignatures)
//                if (CACHING) {
//                    writeDeciperFunctToChache()
//                }
//            } else {
//                return false
//            }
//        } else {
//            decipherViaWebView(encSignatures)
//        }
//        return true
//    }
//
//    @Throws(IOException::class)
//    private fun parseDashManifest(dashMpdUrl: String, ytFiles: SparseArray<YtFile>) {
//        val patBaseUrl = Pattern.compile("<BaseURL yt:contentLength=\"[0-9]+?\">(.+?)</BaseURL>")
//        val dashManifest: String?
//        var reader: BufferedReader? = null
//        val getUrl = URL(dashMpdUrl)
//        val urlConnection = getUrl.openConnection() as HttpURLConnection
//        urlConnection.setRequestProperty("User-Agent", USER_AGENT)
//        try {
//            reader = BufferedReader(InputStreamReader(urlConnection.inputStream))
//            reader.readLine()
//            dashManifest = reader.readLine()
//
//        } finally {
//            if (reader != null)
//                reader.close()
//            urlConnection.disconnect()
//        }
//        if (dashManifest == null)
//            return
//        val mat = patBaseUrl.matcher(dashManifest)
//        while (mat.find()) {
//            val itag: Int
//            var url = mat.group(1)
//            val mat2 = patItag.matcher(url)
//            if (mat2.find()) {
//                itag = Integer.parseInt(mat2.group(1))
//                if (FORMAT_MAP.get(itag) == null)
//                    continue
//                if (!includeWebM && FORMAT_MAP.get(itag).ext == "webm")
//                    continue
//            } else {
//                continue
//            }
//            url = url.replace("&amp;", "&").replace(",", "%2C").replace("mime=audio/", "mime=audio%2F").replace("mime=video/", "mime=video%2F")
//            val yf = YtFile(FORMAT_MAP.get(itag), url)
//            ytFiles.append(itag, yf)
//        }
//
//    }
//
//    @Throws(UnsupportedEncodingException::class)
//    private fun parseVideoMeta(getVideoInfo: String?) {
//        var isLiveStream = false
//        var name: String? = null
//        var author: String? = null
//        var channelId: String? = null
//        var viewCount: Long = 0
//        var length: Long = 0
//        var mat = patTitle.matcher(getVideoInfo!!)
//        if (mat.find()) {
//            name = URLDecoder.decode(mat.group(1), "UTF-8")
//        }
//
//        mat = patHlsvp.matcher(getVideoInfo)
//        if (mat.find())
//            isLiveStream = true
//
//        mat = patAuthor.matcher(getVideoInfo)
//        if (mat.find()) {
//            author = URLDecoder.decode(mat.group(1), "UTF-8")
//        }
//        mat = patChannelId.matcher(getVideoInfo)
//        if (mat.find()) {
//            channelId = mat.group(1)
//        }
//        mat = patLength.matcher(getVideoInfo)
//        if (mat.find()) {
//            length = java.lang.Long.parseLong(mat.group(1))
//        }
//        mat = patViewCount.matcher(getVideoInfo)
//        if (mat.find()) {
//            viewCount = java.lang.Long.parseLong(mat.group(1))
//        }
//        videoMeta = VideoMeta(videoID, name, author, channelId, length, viewCount, isLiveStream)
//
//    }
//
//    private fun readDecipherFunctFromCache() {
//        if (context != null) {
//            val cacheFile = File(context.cacheDir.absolutePath + "/" + CACHE_FILE_NAME)
//            // The cached functions are valid for 2 weeks
//            if (cacheFile.exists() && System.currentTimeMillis() - cacheFile.lastModified() < 1209600000) {
//                var reader: BufferedReader? = null
//                try {
//                    reader = BufferedReader(InputStreamReader(FileInputStream(cacheFile), "UTF-8"))
//                    decipherJsFileName = reader.readLine()
//                    decipherFunctionName = reader.readLine()
//                    decipherFunctions = reader.readLine()
//                } catch (e: Exception) {
//                    e.printStackTrace()
//                } finally {
//                    if (reader != null) {
//                        try {
//                            reader.close()
//                        } catch (e: IOException) {
//                            e.printStackTrace()
//                        }
//
//                    }
//                }
//            }
//        }
//    }
//
//    /**
//     * Parse the dash manifest for different dash streams and high quality audio. Default: false
//     */
//    fun setParseDashManifest(parseDashManifest: Boolean) {
//        this.parseDashManifest = parseDashManifest
//    }
//
//
//    /**
//     * Include the webm format files into the result. Default: true
//     */
//    fun setIncludeWebM(includeWebM: Boolean) {
//        this.includeWebM = includeWebM
//    }
//
//
//    /**
//     * Set default protocol of the returned urls to HTTP instead of HTTPS.
//     * HTTP may be blocked in some regions so HTTPS is the default value.
//     *
//     *
//     * Note: Enciphered videos require HTTPS so they are not affected by
//     * this.
//     */
//    fun setDefaultHttpProtocol(useHttp: Boolean) {
//        this.useHttp = useHttp
//    }
//
//    private fun writeDeciperFunctToChache() {
//        if (context != null) {
//            val cacheFile = File(context.cacheDir.absolutePath + "/" + CACHE_FILE_NAME)
//            var writer: BufferedWriter? = null
//            try {
//                writer = BufferedWriter(OutputStreamWriter(FileOutputStream(cacheFile), "UTF-8"))
//                writer.write(decipherJsFileName!! + "\n")
//                writer.write(decipherFunctionName!! + "\n")
//                writer.write(decipherFunctions!!)
//            } catch (e: Exception) {
//                e.printStackTrace()
//            } finally {
//                if (writer != null) {
//                    try {
//                        writer.close()
//                    } catch (e: IOException) {
//                        e.printStackTrace()
//                    }
//
//                }
//            }
//        }
//    }
//
//    private fun decipherViaWebView(encSignatures: SparseArray<String>) {
//        if (context == null) {
//            return
//        }
//
//        val stb = StringBuilder(decipherFunctions!! + " function decipher(")
//        stb.append("){return ")
//        for (i in 0 until encSignatures.size()) {
//            val key = encSignatures.keyAt(i)
//            if (i < encSignatures.size() - 1)
//                stb.append(decipherFunctionName).append("('").append(encSignatures.get(key)).append("')+\"\\n\"+")
//            else
//                stb.append(decipherFunctionName).append("('").append(encSignatures.get(key)).append("')")
//        }
//        stb.append("};decipher();")
//
//        Handler(Looper.getMainLooper()).post {
//            JsEvaluator(context).evaluate(stb.toString(), object : JsCallback {
//                override fun onResult(result: String) {
//                    lock.lock()
//                    try {
//                        decipheredSignature = result
//                        jsExecuting.signal()
//                    } finally {
//                        lock.unlock()
//                    }
//                }
//
//                override fun onError(errorMessage: String) {
//                    lock.lock()
//                    try {
//                        if (LOGGING)
//                            Log.e(LOG_TAG, errorMessage)
//                        jsExecuting.signal()
//                    } finally {
//                        lock.unlock()
//                    }
//                }
//            })
//        }
//    }
//
//    companion object {
//
//        private val CACHING = true
//
//        internal var LOGGING = false
//
//        private val LOG_TAG = "YouTubeExtractor"
//        private val CACHE_FILE_NAME = "decipher_js_funct"
//        private val DASH_PARSE_RETRIES = 5
//
//        private var decipherJsFileName: String? = null
//        private var decipherFunctions: String? = null
//        private var decipherFunctionName: String? = null
//
//        private val USER_AGENT = "Mozilla/5.0 (Windows NT 6.1; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/40.0.2214.115 Safari/537.36"
//        private val STREAM_MAP_STRING = "url_encoded_fmt_stream_map"
//
//        private val patYouTubePageLink = Pattern.compile("(http|https)://(www\\.|m.|)youtube\\.com/watch\\?v=(.+?)( |\\z|&)")
//        private val patYouTubeShortLink = Pattern.compile("(http|https)://(www\\.|)youtu.be/(.+?)( |\\z|&)")
//
//        private val patDashManifest1 = Pattern.compile("dashmpd=(.+?)(&|\\z)")
//        private val patDashManifest2 = Pattern.compile("\"dashmpd\":\"(.+?)\"")
//        private val patDashManifestEncSig = Pattern.compile("/s/([0-9A-F|.]{10,}?)(/|\\z)")
//
//        private val patTitle = Pattern.compile("name=(.*?)(&|\\z)")
//        private val patAuthor = Pattern.compile("author=(.+?)(&|\\z)")
//        private val patChannelId = Pattern.compile("ucid=(.+?)(&|\\z)")
//        private val patLength = Pattern.compile("length_seconds=(\\d+?)(&|\\z)")
//        private val patViewCount = Pattern.compile("view_count=(\\d+?)(&|\\z)")
//
//        private val patHlsvp = Pattern.compile("hlsvp=(.+?)(&|\\z)")
//        private val patHlsItag = Pattern.compile("/itag/(\\d+?)/")
//
//        private val patItag = Pattern.compile("itag=([0-9]+?)([&,])")
//        private val patEncSig = Pattern.compile("s=([0-9A-F|.]{10,}?)([&,\"])")
//        private val patIsSigEnc = Pattern.compile("s%3D([0-9A-F|.]{10,}?)%26")
//        private val patUrl = Pattern.compile("url=(.+?)([&,])")
//
//        private val patVariableFunction = Pattern.compile("([{; =])([a-zA-Z$][a-zA-Z0-9$]{0,2})\\.([a-zA-Z$][a-zA-Z0-9$]{0,2})\\(")
//        private val patFunction = Pattern.compile("([{; =])([a-zA-Z\$_][a-zA-Z0-9$]{0,2})\\(")
//        private val patDecryptionJsFile = Pattern.compile("jsbin\\\\/(player-(.+?).js)")
//        private val patSignatureDecFunction = Pattern.compile("\"signature\",(.{1,3}?)\\(.{1,10}?\\)")
//
//        private val FORMAT_MAP = SparseArray<Format>()
//
//        init {
//            // http://en.wikipedia.org/wiki/YouTube#Quality_and_formats
//
//            // Video and Audio
//            FORMAT_MAP.put(17, Format(17, "3gp", 144, Format.VCodec.MPEG4, Format.ACodec.AAC, 24, false))
//            FORMAT_MAP.put(36, Format(36, "3gp", 240, Format.VCodec.MPEG4, Format.ACodec.AAC, 32, false))
//            FORMAT_MAP.put(5, Format(5, "flv", 240, Format.VCodec.H263, Format.ACodec.MP3, 64, false))
//            FORMAT_MAP.put(43, Format(43, "webm", 360, Format.VCodec.VP8, Format.ACodec.VORBIS, 128, false))
//            FORMAT_MAP.put(18, Format(18, "mp4", 360, Format.VCodec.H264, Format.ACodec.AAC, 96, false))
//            FORMAT_MAP.put(22, Format(22, "mp4", 720, Format.VCodec.H264, Format.ACodec.AAC, 192, false))
//
//            // Dash Video
//            FORMAT_MAP.put(160, Format(160, "mp4", 144, Format.VCodec.H264, Format.ACodec.NONE, true))
//            FORMAT_MAP.put(133, Format(133, "mp4", 240, Format.VCodec.H264, Format.ACodec.NONE, true))
//            FORMAT_MAP.put(134, Format(134, "mp4", 360, Format.VCodec.H264, Format.ACodec.NONE, true))
//            FORMAT_MAP.put(135, Format(135, "mp4", 480, Format.VCodec.H264, Format.ACodec.NONE, true))
//            FORMAT_MAP.put(136, Format(136, "mp4", 720, Format.VCodec.H264, Format.ACodec.NONE, true))
//            FORMAT_MAP.put(137, Format(137, "mp4", 1080, Format.VCodec.H264, Format.ACodec.NONE, true))
//            FORMAT_MAP.put(264, Format(264, "mp4", 1440, Format.VCodec.H264, Format.ACodec.NONE, true))
//            FORMAT_MAP.put(266, Format(266, "mp4", 2160, Format.VCodec.H264, Format.ACodec.NONE, true))
//
//            FORMAT_MAP.put(298, Format(298, "mp4", 720, Format.VCodec.H264, 60, Format.ACodec.NONE, true))
//            FORMAT_MAP.put(299, Format(299, "mp4", 1080, Format.VCodec.H264, 60, Format.ACodec.NONE, true))
//
//            // Dash Audio
//            FORMAT_MAP.put(140, Format(140, "m4a", Format.VCodec.NONE, Format.ACodec.AAC, 128, true))
//            FORMAT_MAP.put(141, Format(141, "m4a", Format.VCodec.NONE, Format.ACodec.AAC, 256, true))
//
//            // WEBM Dash Video
//            FORMAT_MAP.put(278, Format(278, "webm", 144, Format.VCodec.VP9, Format.ACodec.NONE, true))
//            FORMAT_MAP.put(242, Format(242, "webm", 240, Format.VCodec.VP9, Format.ACodec.NONE, true))
//            FORMAT_MAP.put(243, Format(243, "webm", 360, Format.VCodec.VP9, Format.ACodec.NONE, true))
//            FORMAT_MAP.put(244, Format(244, "webm", 480, Format.VCodec.VP9, Format.ACodec.NONE, true))
//            FORMAT_MAP.put(247, Format(247, "webm", 720, Format.VCodec.VP9, Format.ACodec.NONE, true))
//            FORMAT_MAP.put(248, Format(248, "webm", 1080, Format.VCodec.VP9, Format.ACodec.NONE, true))
//            FORMAT_MAP.put(271, Format(271, "webm", 1440, Format.VCodec.VP9, Format.ACodec.NONE, true))
//            FORMAT_MAP.put(313, Format(313, "webm", 2160, Format.VCodec.VP9, Format.ACodec.NONE, true))
//
//            FORMAT_MAP.put(302, Format(302, "webm", 720, Format.VCodec.VP9, 60, Format.ACodec.NONE, true))
//            FORMAT_MAP.put(308, Format(308, "webm", 1440, Format.VCodec.VP9, 60, Format.ACodec.NONE, true))
//            FORMAT_MAP.put(303, Format(303, "webm", 1080, Format.VCodec.VP9, 60, Format.ACodec.NONE, true))
//            FORMAT_MAP.put(315, Format(315, "webm", 2160, Format.VCodec.VP9, 60, Format.ACodec.NONE, true))
//
//            // WEBM Dash Audio
//            FORMAT_MAP.put(171, Format(171, "webm", Format.VCodec.NONE, Format.ACodec.VORBIS, 128, true))
//
//            FORMAT_MAP.put(249, Format(249, "webm", Format.VCodec.NONE, Format.ACodec.OPUS, 48, true))
//            FORMAT_MAP.put(250, Format(250, "webm", Format.VCodec.NONE, Format.ACodec.OPUS, 64, true))
//            FORMAT_MAP.put(251, Format(251, "webm", Format.VCodec.NONE, Format.ACodec.OPUS, 160, true))
//
//            // HLS Live Stream
//            FORMAT_MAP.put(91, Format(91, "mp4", 144, Format.VCodec.H264, Format.ACodec.AAC, 48, false, true))
//            FORMAT_MAP.put(92, Format(92, "mp4", 240, Format.VCodec.H264, Format.ACodec.AAC, 48, false, true))
//            FORMAT_MAP.put(93, Format(93, "mp4", 360, Format.VCodec.H264, Format.ACodec.AAC, 128, false, true))
//            FORMAT_MAP.put(94, Format(94, "mp4", 480, Format.VCodec.H264, Format.ACodec.AAC, 128, false, true))
//            FORMAT_MAP.put(95, Format(95, "mp4", 720, Format.VCodec.H264, Format.ACodec.AAC, 256, false, true))
//            FORMAT_MAP.put(96, Format(96, "mp4", 1080, Format.VCodec.H264, Format.ACodec.AAC, 256, false, true))
//        }
//    }
//
//}
