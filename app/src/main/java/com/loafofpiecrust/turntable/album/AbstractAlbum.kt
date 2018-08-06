

data class Genre(val name: String)

interface Song {
   val year: Int?
}

interface AbstractAlbum {
   val id: Album.Id
   val tracks: List<Song>
   val year: Int?
   val type: Album.Type
   val genres: List<Genre>
}

data class LocalAlbum(
   override val id: Album.Id,
   override val tracks: List<Song>
): AbstractAlbum {
   override val year: Int?
      get() = tracks.find { it.year != null }?.year
   override val type by lazy {
      Album.Type.LP
   }
   override val genres: List<Genre> // TODO: compile from tracks!
}

data class RemoteAlbum(
   override val id: Album.Id,
   val remoteId: SearchApi.Id, // Discogs, Spotify, or MusicBrainz ID
   override val type: Album.Type = Album.Type.LP,
   override val year: Int? = null,
   override val genres: List<Genre> = listOf()
): AbstractAlbum {
   override val tracks: List<Song> by lazy {
      // grab tracks from online
      listOf<Song>()
   }
}
