package com.loafofpiecrust.turntable.service

//import com.google.android.gms.drive.DriveFile
//import com.google.android.gms.drive.DriveFolder
//import com.google.android.gms.drive.DriveId
//import com.google.android.gms.drive.MetadataChangeSet
//import com.google.android.gms.drive.events.DriveEventService
//import com.google.android.gms.drive.query.Filters
//import com.google.android.gms.drive.query.Query
//import com.google.android.gms.drive.query.SearchableField

//
//class FileSyncService : DriveEventService() {
//    companion object: AnkoLogger {
//        lateinit var instance: FileSyncService
//
////        val SCOPES = arrayOf(
////            DriveScopes.DRIVE_APPDATA,
////            DriveScopes.DRIVE_FILE
////        )
//
//        /** Global instance of the HTTP transport.  */
////        private val HTTP_TRANSPORT = AndroidHttp.newCompatibleTransport()
////        /** Global instance of the JSON factory.  */
////        private val JSON_FACTORY = AndroidJsonFactory.getDefaultInstance()
//
////        val kryo get() = App.kryo
//    }
//
//    init {
//        instance = this
//    }
//
//    private val googleAccount get() = SyncService.googleAccount
////    private val drive: Drive by lazy {
////        val cred = GoogleAccountCredential.usingOAuth2(App.instance, SCOPES.toList()).apply {
////            selectedAccount = googleAccount?.account
////        }
////        Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, cred).apply {
////            applicationName = "Turntable"
////        }.build()
////    }
//
////    private val jsonMapper = jacksonObjectMapper()
////        .findAndRegisterModules()
////        .registerModule(SimpleModule().addSerializer(behaviorSubjectSerializer<>()))
////        .enableDefaultTypingAsProperty(
////            ObjectMapper.DefaultTyping.OBJECT_AND_NON_CONCRETE, "@class"
////        )
//    var gclient: GoogleApiClient? = null
//
//    private val _playlists = ConflatedBroadcastChannel(listOf<Playlist>())
//    val playlists: ReceiveChannel<List<Playlist>> get() = _playlists.openSubscription()
//    private val subscriptions = HashMap<Playlist, Job>()
//
////    override fun onChange(change: ChangeEvent) {
////        return
////        println("library: file change evt ${change}")
////        val fileId = change.driveId.encodeToString()
////        task {
////            // Update the corresponding playlist
////            val allPls = _playlists.value
////            val plIdx = allPls.indexOfFirst { it.remoteFileId == fileId }
////            if (plIdx != -1) {
////                if (change.hasBeenDeleted()) {
////                    // Remove playlist in memory
////                    // playlists puts playlists.without(playlists.indexOf(playlist))
////                    _playlists puts allPls.without(plIdx)
////                } else if (change.hasContentChanged()) {
////                    // Update playlist content in memory
////                    val pl = allPls[plIdx]
////                    val newPl = readPlaylistSync(change.driveId.asDriveFile())
//////                    subscriptions[pl]?.dispose()
////                    if (newPl != null) {
////                        pl.diffAndMerge(newPl)
////                    }
//////                    _playlists puts allPls.withReplaced(plIdx, newPl)
////                } else if (change.hasMetadataChanged()) {
////                    // Maybe the uuid changed.
////                    // I think this event doesn't matter
////                }
////            } else {
//////                _playlists puts _playlists.value + readPlaylistSync()
////            }
////        }
////    }
//
//    fun setupSync(gclient: GoogleApiClient) {
//        this.gclient = gclient
////        return
//        task {
//            if (!gclient.isConnected) {
//                gclient.blockingConnect()
//            }
//
//            _playlists puts readAllPlaylistsSync()
//        }
//    }
//
//    override fun onCreate() {
//        super.onCreate()
//
//        // Setup automatic playlist syncing here, using observables!
//        // Should need no other code to even touch this class. All can be done with rx streams :D
////        Library.with(ctx) { library ->
////            // Sync playlists when the app is first opened.
////            // Playlists won't be added remotely to your folder without a direct message (on FCM?)
////            // So, we can assume we don't need to pull down new files after initial sync.
////            // But, we _do_ an initial sync in case this is on a new device, post-restore, etc.
////            task {
////                val pls = library.playlists.awaitFirst()
////                syncPlaylists(pls)
////            }
////        }
//    }
//
////    private suspend fun makeDir(parent: String, folderName: String): Result<File, String> = run {
////        val meta = File().apply {
////            parents = listOf(parent)
////            uuid = folderName
////            mimeType = "application/vnd.google-apps.folder"
////        }
////
////        val err = Result.Error<File, String>("Failed to create folder '$parent/$folderName'")
////        try {
////            val file = drive.files().create(meta).apply {
////                fields = "uuid"
////            }.execute()
////
////            if (file != null) {
////                Result.Ok(file)
////            } else err
////        } catch(e: Exception) {
////            async(UI) { e.printStackTrace() }
////            err
////        }
////    }
//
////    private suspend fun makeFile(parent: String, fileName: String, cb: File.() -> Unit): Result<File, String> {
////        val meta = File().apply {
////            parents = listOf(parent)
////            uuid = fileName
////            writersCanShare = true
////            cb()
////        }
////
////        val file: File = drive.files().create(meta).apply {
////            fields = "uuid"
////        }.execute()
////
////        return if (file != null) {
////            Result.Ok(file)
////        } else {
////            Result.Error("Failed to create drive file '$parent/$fileName'")
////        }
////    }
//
//
////    private suspend fun makePlaylistFolder(): Result<File, String> = run {
////        val existing = drive.files().list().apply {
////            spaces = "appDataFolder"
////            q = "'appDataFolder' in parents and uuid = 'Playlists'"
////        }.execute()
////
////        if (existing.files != null && existing.files.isNotEmpty()) {
////            Result.Ok(existing.files.first())
////        } else {
////            makeDir("appDataFolder", "Playlists")
////        }
////    }
//
////    suspend fun uploadPlaylist(playlist: Playlist) {
//////        val json = jsonMapper.typedToJson(playlist)
////        val json = jsonMapper.writeValueAsBytes(playlist)
////        val content = ByteArrayContent("application/json", json)
////
////        if (playlist.remoteFileId == null) {
////            val folder = makePlaylistFolder().let {
////                when (it) {
////                    is Result.Error -> {
////                        async(UI) { println("library: error, ${it.error}") }
////                        return
////                    }
////                    is Result.Ok -> it.ok
////                }
////            }
////
////            // The playlist file doesn't exist, create it with our json content.
////            val meta = File().apply {
////                mimeType = "application/json"
////                parents = listOf(folder.uuid)
////                uuid = "${playlist.uuid}.json"
//////                writersCanShare = true
////            }
////
////            try {
////                val file = drive.files().create(meta).apply {
////                    fields = "uuid"
////                }.execute()
////                playlist.remoteFileId = file.uuid
////            } catch (e: Exception) {
////                async(UI) {
////                    e.printStackTrace()
////                    println("library: failed to upload playlist, '${e.message}'")
////                }
////            }
////        }
////
////        if (playlist.remoteFileId != null) {
////            drive.files().update(playlist.remoteFileId, null, content).execute()
////        }
////    }
//
////    suspend fun downloadPlaylist(playlist: Playlist): Result<Playlist, String> = run {
////        if (playlist.remoteFileId != null) {
////            val os = ByteArrayOutputStream()
////            drive.files().get(playlist.remoteFileId).executeMediaAndDownloadTo(os)
////
////            try {
////                jsonMapper.readValue<Playlist>(os.toByteArray()).asOk<Playlist, String>()
////            } catch (e: Exception) {
////                async(UI) { e.printStackTrace() }
////                "Failed to map string to json playlist instance".asError<Playlist, String>()
////            }
////        } else {
////            Result.Error("Didn't find file for playlist '${playlist.uuid}'")
////        }
////    }
//
////    private suspend fun downloadAllPlaylists(): Result<List<Playlist>, String> {
////        val folder = makePlaylistFolder()
////
////        return folder.map { folder ->
////            drive.files().list().apply {
////                spaces = "appDataFolder"
////                q = "'${folder.uuid}' in parents"
////                fields = "files(uuid, uuid)"
////            }.execute().files.map {
////                async(CommonPool) {
////                    val os = ByteArrayOutputStream()
////                    drive.files().get(it.uuid).executeMediaAndDownloadTo(os)
////                    jsonMapper.readValue<Playlist>(os.toByteArray())
////                }
////            }.map { it.await() }
////        }
////    }
//
////    suspend fun deletePlaylist(playlist: Playlist) = run {
////        if (playlist.remoteFileId != null) {
////            drive.files().delete(playlist.remoteFileId).execute()
////        }
////    }
//
////    private suspend fun syncPlaylists(playlists: List<Playlist>) {
////        // Sync them all
////        val remotes = downloadAllPlaylists()
////        // now, diff remotes and locals
//////        val locals = Library.instance.playlists.blockingFirst().toMutableList()
////        val locals = playlists.toMutableList()
////
////        val merged = ArrayList<Playlist>()
////
////        if (remotes is Result.Ok) {
////            remotes.ok.forEach { remote ->
////                val local = locals.find { it.uuid == remote.uuid }
////                merged += if (local == null || remote.lastModified > local.lastModified) {
////                    // No local playlist for this one, just add it.
////                    remote
////                } else {
////                    // Found a local version.
////                    locals.remove(local)
////                    local
////                }
////            }
////
//////            UserPrefs.playlists puts merged.toTypedArray()
////        } else {
////            println("library: playlist sync error, ${remotes.error()}")
////        }
////    }
//
////    fun shareFile(driveId: DriveId, writable: Boolean) {
////        val resId = driveId.resourceId
////        drive.permissions().create(resId, Permission().apply {
////            type = "anyone"
////            allowFileDiscovery = false // Searchable?
////            role = if (writable) "writer" else "reader"
////        }).execute()
////    }
//
//    fun makeMainFolderSync(): DriveFolder? {
//        if (gclient == null) return null
//        return try {
//            val mainFolderName = "com.loafofpiecrust.turntable"
//            val root = com.google.android.gms.drive.Drive.DriveApi.getRootFolder(gclient)
//
//            // Check if it exists first
//            val existing = root.queryChildren(gclient, Query.Builder()
//                .addFilter(Filters.eq(SearchableField.TITLE, mainFolderName))
//                .build()
//            ).await().metadataBuffer
//
//            if (existing.count > 0) {
//                // Exists!
//                existing[0].driveId.asDriveFolder()
//            } else {
//                val meta = MetadataChangeSet.Builder().apply {
//                    setTitle(mainFolderName)
//                }.build()
//                root.createFolder(gclient, meta).await().driveFolder
//            }
//        } catch (e: Exception) {
//            async(UI) {
//                println("library: failed to make app folder")
//                e.printStackTrace()
//            }
//            null
//        }
//    }
//
//    fun makePlaylistFolderSync(): DriveFolder? {
//        val mainFolder = makeMainFolderSync() ?: return null
//        val existing = mainFolder.queryChildren(gclient, Query.Builder()
//            .addFilter(Filters.eq(SearchableField.TITLE, "playlists"))
//            .build()
//        ).await().metadataBuffer
//
//        return if (existing.count > 0) {
//            existing[0].driveId.asDriveFolder()
//        } else {
//            val meta = MetadataChangeSet.Builder().apply {
//                setTitle("playlists")
//            }.build()
//            mainFolder.createFolder(gclient, meta).await().driveFolder
//        }
//    }
//
//    fun grabPlaylistFile(pl: Playlist): DriveFile? {
//        if (gclient == null) return null
//        return if (pl.remoteFileId != null) {
//            DriveId.decodeFromString(pl.remoteFileId).asDriveFile()
////            com.google.android.gms.drive.Drive.DriveApi
////                .fetchDriveId(gclient, pl.remoteFileId)
////                .await().driveId.asDriveFile()
//        } else {
//            val folder = makePlaylistFolderSync()
//            val meta = MetadataChangeSet.Builder().apply {
//                setTitle(pl.name)
//            }.build()
////            val contents = com.google.android.gms.drive.Drive.DriveApi.newDriveContents(gclient).await().driveContents
//            val file = folder?.createFile(gclient, meta, null)?.await()?.driveFile ?: return null
//            pl.remoteFileId = file.driveId.toString()
//            file
//        }
//    }
//
//    fun addPlaylist(pl: Playlist) {
//        _playlists puts (_playlists.value ?: listOf()) + pl
////        pl.tracks.subscribe(CommonPool) {
//        task {
//            writePlaylistSync(pl)
//        }
////        }
//    }
//
//    fun deletePlaylist(pl: Playlist) {
//        _playlists puts _playlists.value.without(_playlists.value.indexOf(pl))
//        if (pl.remoteFileId != null) {
//            val file = DriveId.decodeFromString(pl.remoteFileId).asDriveFile()
//            file.delete(gclient)
//        }
//    }
//
//    fun writePlaylistSync(pl: Playlist) {
//        val file = grabPlaylistFile(pl) ?: return
//
//        // Should happen immediately and upon subsequent changes to the playlist tracks
//        subscriptions.put(pl, pl.tracks.consumeEach(BG_POOL) {
//            debug("library: writing changed playlist to drive")
//            task {
//                try {
//                    val contents = file.open(gclient, DriveFile.MODE_WRITE_ONLY, null)
//                        .await().driveContents
//
////            jsonMapper.writeValue(contents.outputStream, pl)
//                    val os = Output(contents.outputStream)
////                val json = gson.typedToJson(pl)
////                async(UI) { println("library: playlist json! $json") }
//
////                os.write(json.toByteArray())
//                    kryo.writeClassAndObject(os, pl)
//                    os.flush()
//                    contents.commit(gclient, null).await()
////                os.closeQuietly()
//                } catch (e: Throwable) {
//                    verbose("Failed to write playlist to drive file", e)
//                }
//            }
//        })
//
////        file.addChangeSubscription(gclient)
//    }
//
//    fun readPlaylistSync(pl: Playlist): Playlist? {
//        return readPlaylistSync(grabPlaylistFile(pl) ?: return null)
//    }
//
//    fun readPlaylistSync(file: DriveFile): Playlist? {
//        if (gclient == null) return null
//
//        val contents = file.open(gclient, DriveFile.MODE_READ_ONLY, null)
//            .await().driveContents
//
//        val input = Input(contents.inputStream)
//        val pl = kryo.readClassAndObject(input) as Playlist
////        input.closeQuietly()
//        return pl
//
////        file.addChangeSubscription(gclient)
////        return jsonMapper.readValue(contents.inputStream)
////        return gson.fromJson<Playlist>(contents.inputStream.reader())
//
//    }
//
//    fun readAllPlaylistsSync(): List<Playlist> {
//        debug("library: reading playlists from files")
//        val folder = makePlaylistFolderSync() ?: return listOf()
//        debug("library: got the folder!")
//        val files = folder.listChildren(gclient).await().metadataBuffer
//        return files.map {
//            val uuid = it.driveId
//            val file = it.driveId.asDriveFile()
//            val meta = file.getMetadata(gclient).await().metadata
//            if (meta.isTrashed) {
//                return@map null
//            }
//
//            task {
//                try {
//                    val contents = file.open(gclient, DriveFile.MODE_READ_ONLY, null).await().driveContents
//                    val input = Input(contents.inputStream)
////                    val playlist = jsonMapper.readValue<Playlist>(contents.inputStream)
////                    val playlist = gson.fromJson<Playlist>(contents.inputStream.reader(), typeToken<Playlist>())
//                    val playlist = kryo.readClassAndObject(input) as Playlist
////                    input.closeQuietly()
//                    playlist.remoteFileId = uuid.encodeToString()
//
//                    subscriptions[playlist] = playlist.tracks.skip(1).distinctSeq().consumeEach(BG_POOL) {
//                        debug("library: writing changed playlist to drive")
//                        val contents = file.open(gclient, DriveFile.MODE_WRITE_ONLY, null)
//                            .await().driveContents
//
//            //                        jsonMapper.writeValue(contents.outputStream, playlist)
//                        val os = Output(contents.outputStream)
//                        kryo.writeClassAndObject(os, playlist)
//            //                        os.write(gson.typedToJson(playlist).toByteArray())
//                        os.flush()
//                        contents.commit(gclient, null).await()
//            //                        os.closeQuietly()
//                    }
//
////                    file.addChangeSubscription(gclient)
//
//                    playlist
//                } catch (e: Exception) {
//                    verbose("Failed to read playlist file.", e)
//                    null
//                }
//            }
//        }.mapNotNull { it?.get() }
//    }
//}