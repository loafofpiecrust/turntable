
# Turntable
Music player allowing a user to
* play their local library of music (on phone SD card, for example)
* browse various music databases
* stream music for free (directly from YouTube)
* sync their listening session with other users in real-time
* build playlists
* recommend music to other users within the app to easier listen to many recommendations


### Data Providers
* Discogs: extensive library, but based on physical releases
    * Default `Repository` for metadata
    * Default for all searches and discographies
* Spotify: decent library, but tied to who service-based choices
    * migrate playlists,
    * get recommendations (+ radios)
    * similar artists
* MusicBrainz: robust music metadata, sometimes out of date.
* Last.FM: backup for album & artist artwork, basically a messy subset of Spotify.
* Bandcamp: something to look into
    * Extensive libraries for obscure artists that host and sell on Bandcamp
    * HTML scraping could give us a streaming provider here.
