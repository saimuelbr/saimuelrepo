// use an integer for version numbers
version = 1


cloudstream {
    description = "Filmes, Séries e Animes do VisionCine com qualidade HD e legendas"
    language    = "pt-br"
    authors = listOf("saimuelbr")

    /**
    * Status int as the following:
    * 0: Down
    * 1: Ok
    * 2: Slow
    * 3: Beta only
    * */
    status = 1 // will be 3 if unspecified

    // List of video source types. Users are able to filter for extensions in a given category.
    // You can find a list of available types here:
    // https://recloudstream.github.io/cloudstream/html/app/com.lagradost.cloudstream3/-tv-type/index.html
    tvTypes = listOf("Movie","TvSeries")
    iconUrl = "https://www.visioncine-1.com.br/temas/pixer/assets/img/icon_free.png"

    isCrossPlatform = true
} 