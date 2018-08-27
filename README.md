
### APIs in use
* Last.FM (grab related artists & album covers. Convert to use Spotify?)
* Spotify (convert playlists & get recommendations)
* MusicBrainz (music metadata)
*



# Design Decisions
### Coroutines & Lifecycles
Problem: We want to use coroutines and channels to retrieve data and operate on our views.
For example: when we search for an artist with a text query, we dispatch the search request to our HTTP client and await a response asynchronously (using a coroutine)
