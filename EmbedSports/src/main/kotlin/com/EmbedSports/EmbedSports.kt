package com.EmbedSports

import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newLiveSearchResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.newExtractorLink

class EmbedSports : MainAPI() {
    override var name = "EmbedSports"
    override val hasMainPage = true
    override var lang = "pt-br"
    override val hasDownloadSupport = false
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.Live)


    private val channelList = listOf(
        mapOf(
            "nome" to "Adult Swim",
            "url" to "https://embedcanais.com/adultswim",
            "img" to "https://embedcanais.com/images/adultswim.png",
            "category" to "entretenimento",
            "category_name" to "Entretenimento"
        ),
        mapOf(
            "nome" to "A&E",
            "url" to "https://embedcanais.com/aee",
            "img" to "https://embedcanais.com/images/aee.png",
            "category" to "entretenimento",
            "category_name" to "Entretenimento"
        ),
        mapOf(
            "nome" to "AMC",
            "url" to "https://embedcanais.com/amc",
            "img" to "https://embedcanais.com/images/amc.png",
            "category" to "entretenimento",
            "category_name" to "Entretenimento"
        ),
        mapOf(
            "nome" to "Animal Planet",
            "url" to "https://embedcanais.com/animalplanet",
            "img" to "https://embedcanais.com/images/animalplanet.png",
            "category" to "documentarios",
            "category_name" to "Documentários"
        ),
        mapOf(
            "nome" to "Arte 1",
            "url" to "https://embedcanais.com/arte1",
            "img" to "https://embedcanais.com/images/arte1.png",
            "category" to "cultura",
            "category_name" to "Cultura"
        ),
        mapOf(
            "nome" to "AXN",
            "url" to "https://embedcanais.com/axn",
            "img" to "https://embedcanais.com/images/axn.png",
            "category" to "entretenimento",
            "category_name" to "Entretenimento"
        ),
        mapOf(
            "nome" to "BandNews",
            "url" to "https://embedcanais.com/bandnews",
            "img" to "https://embedcanais.com/images/bandnews.png",
            "category" to "noticias",
            "category_name" to "Notícias"
        ),
        mapOf(
            "nome" to "Band SP",
            "url" to "https://embedcanais.com/bandsp",
            "img" to "https://embedcanais.com/images/bandsp.png",
            "category" to "tv-aberta",
            "category_name" to "TV Aberta"
        ),
        mapOf(
            "nome" to "BandSports",
            "url" to "https://embedcanais.com/bandsports",
            "img" to "https://embedcanais.com/images/bandsports.png",
            "category" to "esportes",
            "category_name" to "Esportes"
        ),
        mapOf(
            "nome" to "Canção Nova",
            "url" to "https://embedcanais.com/cancaonova",
            "img" to "https://embedcanais.com/images/cancaonova.png",
            "category" to "religiao",
            "category_name" to "Religião"
        ),
        mapOf(
            "nome" to "Cartoonito",
            "url" to "https://embedcanais.com/cartoonito",
            "img" to "https://embedcanais.com/images/cartoonito.png",
            "category" to "infantil",
            "category_name" to "Infantil"
        ),
        mapOf(
            "nome" to "Cartoon Network",
            "url" to "https://embedcanais.com/cartoonnetwork",
            "img" to "https://embedcanais.com/images/cartoonnetwork.png",
            "category" to "infantil",
            "category_name" to "Infantil"
        ),
        mapOf(
            "nome" to "CazéTV",
            "url" to "https://embedcanais.com/cazetv",
            "img" to "https://embedcanais.com/images/cazetv.png",
            "category" to "esportes",
            "category_name" to "Esportes"
        ),
        mapOf(
            "nome" to "Cinemax",
            "url" to "https://embedcanais.com/cinemax",
            "img" to "https://embedcanais.com/images/cinemax.png",
            "category" to "filmes",
            "category_name" to "Filmes"
        ),
        mapOf(
            "nome" to "CNN Brasil",
            "url" to "https://embedcanais.com/cnnbrasil",
            "img" to "https://embedcanais.com/images/cnnbrasil.png",
            "category" to "noticias",
            "category_name" to "Notícias"
        ),
        mapOf(
            "nome" to "Combate",
            "url" to "https://embedcanais.com/combate",
            "img" to "https://embedcanais.com/images/combate.png",
            "category" to "esportes",
            "category_name" to "Esportes"
        ),
        mapOf(
            "nome" to "Comedy Central",
            "url" to "https://embedcanais.com/comedycentral",
            "img" to "https://embedcanais.com/images/comedycentral.png",
            "category" to "entretenimento",
            "category_name" to "Entretenimento"
        ),
        mapOf(
            "nome" to "Darkflix",
            "url" to "https://embedcanais.com/darkflix",
            "img" to "https://embedcanais.com/images/darkflix.png",
            "category" to "filmes",
            "category_name" to "Filmes"
        ),
        mapOf(
            "nome" to "DAZN",
            "url" to "https://embedcanais.com/dazn",
            "img" to "https://embedcanais.com/images/dazn.png",
            "category" to "esportes",
            "category_name" to "Esportes"
        ),
        mapOf(
            "nome" to "Discovery Channel",
            "url" to "https://embedcanais.com/discoverychannel",
            "img" to "https://embedcanais.com/images/discoverychannel.png",
            "category" to "documentarios",
            "category_name" to "Documentários"
        ),
        mapOf(
            "nome" to "Discovery Home & Health",
            "url" to "https://embedcanais.com/discoveryhh",
            "img" to "https://embedcanais.com/images/discoveryhh.png",
            "category" to "lifestyle",
            "category_name" to "Lifestyle"
        ),
        mapOf(
            "nome" to "Investigation Discovery",
            "url" to "https://embedcanais.com/discoveryid",
            "img" to "https://embedcanais.com/images/discoveryid.png",
            "category" to "documentarios",
            "category_name" to "Documentários"
        ),
        mapOf(
            "nome" to "Discovery Kids",
            "url" to "https://embedcanais.com/discoverykids",
            "img" to "https://embedcanais.com/images/discoverykids.png",
            "category" to "infantil",
            "category_name" to "Infantil"
        ),
        mapOf(
            "nome" to "Discovery Science",
            "url" to "https://embedcanais.com/discoveryscience",
            "img" to "https://embedcanais.com/images/discoveryscience.png",
            "category" to "documentarios",
            "category_name" to "Documentários"
        ),
        mapOf(
            "nome" to "Discovery Theater",
            "url" to "https://embedcanais.com/discoverytheater",
            "img" to "https://embedcanais.com/images/discoverytheater.png",
            "category" to "documentarios",
            "category_name" to "Documentários"
        ),
        mapOf(
            "nome" to "Discovery Turbo",
            "url" to "https://embedcanais.com/discoveryturbo",
            "img" to "https://embedcanais.com/images/discoveryturbo.png",
            "category" to "documentarios",
            "category_name" to "Documentários"
        ),
        mapOf(
            "nome" to "Discovery World",
            "url" to "https://embedcanais.com/discoveryworld",
            "img" to "https://embedcanais.com/images/discoveryworld.png",
            "category" to "documentarios",
            "category_name" to "Documentários"
        ),
        mapOf(
            "nome" to "Disney Channel",
            "url" to "https://embedcanais.com/disneychannel",
            "img" to "https://embedcanais.com/images/disneychannel.png",
            "category" to "infantil",
            "category_name" to "Infantil"
        ),
        mapOf(
            "nome" to "Disney+",
            "url" to "https://embedcanais.com/disneyplus",
            "img" to "https://embedcanais.com/images/disneyplus.png",
            "category" to "streaming",
            "category_name" to "Streaming"
        ),
        mapOf(
            "nome" to "DreamWorks Channel",
            "url" to "https://embedcanais.com/dreamworkschannel",
            "img" to "https://embedcanais.com/images/dreamworkschannel.png",
            "category" to "infantil",
            "category_name" to "Infantil"
        ),
        mapOf(
            "nome" to "ESPN",
            "url" to "https://embedcanais.com/espn",
            "img" to "https://embedcanais.com/images/espn.png",
            "category" to "esportes",
            "category_name" to "Esportes"
        ),
        mapOf(
            "nome" to "ESPN 2",
            "url" to "https://embedcanais.com/espn2",
            "img" to "https://embedcanais.com/images/espn2.png",
            "category" to "esportes",
            "category_name" to "Esportes"
        ),
        mapOf(
            "nome" to "ESPN 3",
            "url" to "https://embedcanais.com/espn3",
            "img" to "https://embedcanais.com/images/espn3.png",
            "category" to "esportes",
            "category_name" to "Esportes"
        ),
        mapOf(
            "nome" to "ESPN 4",
            "url" to "https://embedcanais.com/espn4",
            "img" to "https://embedcanais.com/images/espn4.png",
            "category" to "esportes",
            "category_name" to "Esportes"
        ),
        mapOf(
            "nome" to "ESPN 5",
            "url" to "https://embedcanais.com/espn5",
            "img" to "https://embedcanais.com/images/espn5.png",
            "category" to "esportes",
            "category_name" to "Esportes"
        ),
        mapOf(
            "nome" to "ESPN 6",
            "url" to "https://embedcanais.com/espn6",
            "img" to "https://embedcanais.com/images/espn6.png",
            "category" to "esportes",
            "category_name" to "Esportes"
        ),
        mapOf(
            "nome" to "Fish TV",
            "url" to "https://embedcanais.com/fishtv",
            "img" to "https://embedcanais.com/images/fishtv.png",
            "category" to "esportes",
            "category_name" to "Esportes"
        ),
        mapOf(
            "nome" to "Food Network",
            "url" to "https://embedcanais.com/foodnetwork",
            "img" to "https://embedcanais.com/images/foodnetwork.png",
            "category" to "lifestyle",
            "category_name" to "Lifestyle"
        ),
        mapOf(
            "nome" to "Globo DF",
            "url" to "https://embedcanais.com/globodf",
            "img" to "https://embedcanais.com/images/globodf.png",
            "category" to "tv-aberta",
            "category_name" to "TV Aberta"
        ),
        mapOf(
            "nome" to "Globo MG",
            "url" to "https://embedcanais.com/globomg",
            "img" to "https://embedcanais.com/images/globomg.png",
            "category" to "tv-aberta",
            "category_name" to "TV Aberta"
        ),
        mapOf(
            "nome" to "GloboNews",
            "url" to "https://embedcanais.com/globonews",
            "img" to "https://embedcanais.com/images/globonews.png",
            "category" to "noticias",
            "category_name" to "Notícias"
        ),
        mapOf(
            "nome" to "Globoplay Novelas",
            "url" to "https://embedcanais.com/globoplaynovelas",
            "img" to "https://embedcanais.com/images/globoplaynovelas.png",
            "category" to "entretenimento",
            "category_name" to "Entretenimento"
        ),
        mapOf(
            "nome" to "Globo RJ",
            "url" to "https://embedcanais.com/globorj",
            "img" to "https://embedcanais.com/images/globorj.png",
            "category" to "tv-aberta",
            "category_name" to "TV Aberta"
        ),
        mapOf(
            "nome" to "Globo RS",
            "url" to "https://embedcanais.com/globors",
            "img" to "https://embedcanais.com/images/globors.png",
            "category" to "tv-aberta",
            "category_name" to "TV Aberta"
        ),
        mapOf(
            "nome" to "Globo SP",
            "url" to "https://embedcanais.com/globosp",
            "img" to "https://embedcanais.com/images/globosp.png",
            "category" to "tv-aberta",
            "category_name" to "TV Aberta"
        ),
        mapOf(
            "nome" to "Gloob",
            "url" to "https://embedcanais.com/gloob",
            "img" to "https://embedcanais.com/images/gloob.png",
            "category" to "infantil",
            "category_name" to "Infantil"
        ),
        mapOf(
            "nome" to "Gloobinho",
            "url" to "https://embedcanais.com/gloobinho",
            "img" to "https://embedcanais.com/images/gloobinho.png",
            "category" to "infantil",
            "category_name" to "Infantil"
        ),
        mapOf(
            "nome" to "GNT",
            "url" to "https://embedcanais.com/gnt",
            "img" to "https://embedcanais.com/images/gnt.png",
            "category" to "lifestyle",
            "category_name" to "Lifestyle"
        ),
        mapOf(
            "nome" to "HBO",
            "url" to "https://embedcanais.com/hbo",
            "img" to "https://embedcanais.com/images/hbo.png",
            "category" to "filmes",
            "category_name" to "Filmes"
        ),
        mapOf(
            "nome" to "HBO 2",
            "url" to "https://embedcanais.com/hbo2",
            "img" to "https://embedcanais.com/images/hbo2.png",
            "category" to "filmes",
            "category_name" to "Filmes"
        ),
        mapOf(
            "nome" to "HBO Family",
            "url" to "https://embedcanais.com/hbofamily",
            "img" to "https://embedcanais.com/images/hbofamily.png",
            "category" to "filmes",
            "category_name" to "Filmes"
        ),
        mapOf(
            "nome" to "HBO Mundi",
            "url" to "https://embedcanais.com/hbomundi",
            "img" to "https://embedcanais.com/images/hbomundi.png",
            "category" to "entretenimento",
            "category_name" to "Entretenimento"
        ),
        mapOf(
            "nome" to "HBO Plus",
            "url" to "https://embedcanais.com/hboplus",
            "img" to "https://embedcanais.com/images/hboplus.png",
            "category" to "filmes",
            "category_name" to "Filmes"
        ),
        mapOf(
            "nome" to "HBO Pop",
            "url" to "https://embedcanais.com/hbopop",
            "img" to "https://embedcanais.com/images/hbopop.png",
            "category" to "entretenimento",
            "category_name" to "Entretenimento"
        ),
        mapOf(
            "nome" to "HBO Signature",
            "url" to "https://embedcanais.com/hbosignature",
            "img" to "https://embedcanais.com/images/hbosignature.png",
            "category" to "filmes",
            "category_name" to "Filmes"
        ),
        mapOf(
            "nome" to "HBO Xtreme",
            "url" to "https://embedcanais.com/hboxtreme",
            "img" to "https://embedcanais.com/images/hboxtreme.png",
            "category" to "filmes",
            "category_name" to "Filmes"
        ),
        mapOf(
            "nome" to "HGTV",
            "url" to "https://embedcanais.com/hgtv",
            "img" to "https://embedcanais.com/images/hgtv.png",
            "category" to "lifestyle",
            "category_name" to "Lifestyle"
        ),
        mapOf(
            "nome" to "History",
            "url" to "https://embedcanais.com/history",
            "img" to "https://embedcanais.com/images/history.png",
            "category" to "documentarios",
            "category_name" to "Documentários"
        ),
        mapOf(
            "nome" to "History 2",
            "url" to "https://embedcanais.com/history2",
            "img" to "https://embedcanais.com/images/history2.png",
            "category" to "documentarios",
            "category_name" to "Documentários"
        ),
        mapOf(
            "nome" to "Jovem Pan News",
            "url" to "https://embedcanais.com/jovempannews",
            "img" to "https://embedcanais.com/images/jovempannews.png",
            "category" to "noticias",
            "category_name" to "Notícias"
        ),
        mapOf(
            "nome" to "Lifetime",
            "url" to "https://embedcanais.com/lifetime",
            "img" to "https://embedcanais.com/images/lifetime.png",
            "category" to "entretenimento",
            "category_name" to "Entretenimento"
        ),
        mapOf(
            "nome" to "Max",
            "url" to "https://embedcanais.com/max",
            "img" to "https://embedcanais.com/images/max.png",
            "category" to "streaming",
            "category_name" to "Streaming"
        ),
        mapOf(
            "nome" to "Megapix",
            "url" to "https://embedcanais.com/megapix",
            "img" to "https://embedcanais.com/images/megapix.png",
            "category" to "filmes",
            "category_name" to "Filmes"
        ),
        mapOf(
            "nome" to "MTV",
            "url" to "https://embedcanais.com/mtv",
            "img" to "https://embedcanais.com/images/mtv.png",
            "category" to "musica",
            "category_name" to "Música"
        ),
        mapOf(
            "nome" to "Multishow",
            "url" to "https://embedcanais.com/multishow",
            "img" to "https://embedcanais.com/images/multishow.png",
            "category" to "entretenimento",
            "category_name" to "Entretenimento"
        ),
        mapOf(
            "nome" to "National Geographic",
            "url" to "https://embedcanais.com/nationalgeographic",
            "img" to "https://embedcanais.com/images/nationalgeographic.png",
            "category" to "documentarios",
            "category_name" to "Documentários"
        ),
        mapOf(
            "nome" to "Nickelodeon",
            "url" to "https://embedcanais.com/nickelodeon",
            "img" to "https://embedcanais.com/images/nickelodeon.png",
            "category" to "infantil",
            "category_name" to "Infantil"
        ),
        mapOf(
            "nome" to "Nick Jr.",
            "url" to "https://embedcanais.com/nickjr",
            "img" to "https://embedcanais.com/images/nickjr.png",
            "category" to "infantil",
            "category_name" to "Infantil"
        ),
        mapOf(
            "nome" to "Nosso Futebol",
            "url" to "https://embedcanais.com/nossofutebol",
            "img" to "https://embedcanais.com/images/nossofutebol.png",
            "category" to "esportes",
            "category_name" to "Esportes"
        ),
        mapOf(
            "nome" to "Novo Tempo",
            "url" to "https://embedcanais.com/novotempo",
            "img" to "https://embedcanais.com/images/novotempo.png",
            "category" to "religiao",
            "category_name" to "Religião"
        ),
        mapOf(
            "nome" to "Pai Eterno",
            "url" to "https://embedcanais.com/paieterno",
            "img" to "https://embedcanais.com/images/paieterno.png",
            "category" to "religiao",
            "category_name" to "Religião"
        ),
        mapOf(
            "nome" to "Paramount Network",
            "url" to "https://embedcanais.com/paramountnetwork",
            "img" to "https://embedcanais.com/images/paramountnetwork.png",
            "category" to "entretenimento",
            "category_name" to "Entretenimento"
        ),
        mapOf(
            "nome" to "Premiere",
            "url" to "https://embedcanais.com/premiere",
            "img" to "https://embedcanais.com/images/premiere.png",
            "category" to "esportes",
            "category_name" to "Esportes"
        ),
        mapOf(
            "nome" to "Premiere 2",
            "url" to "https://embedcanais.com/premiere2",
            "img" to "https://embedcanais.com/images/premiere2.png",
            "category" to "esportes",
            "category_name" to "Esportes"
        ),
        mapOf(
            "nome" to "Premiere 3",
            "url" to "https://embedcanais.com/premiere3",
            "img" to "https://embedcanais.com/images/premiere3.png",
            "category" to "esportes",
            "category_name" to "Esportes"
        ),
        mapOf(
            "nome" to "Premiere 4",
            "url" to "https://embedcanais.com/premiere4",
            "img" to "https://embedcanais.com/images/premiere4.png",
            "category" to "esportes",
            "category_name" to "Esportes"
        ),
        mapOf(
            "nome" to "Premiere 5",
            "url" to "https://embedcanais.com/premiere5",
            "img" to "https://embedcanais.com/images/premiere5.png",
            "category" to "esportes",
            "category_name" to "Esportes"
        ),
        mapOf(
            "nome" to "Premiere 6",
            "url" to "https://embedcanais.com/premiere6",
            "img" to "https://embedcanais.com/images/premiere6.png",
            "category" to "esportes",
            "category_name" to "Esportes"
        ),
        mapOf(
            "nome" to "Premiere 7",
            "url" to "https://embedcanais.com/premiere7",
            "img" to "https://embedcanais.com/images/premiere7.png",
            "category" to "esportes",
            "category_name" to "Esportes"
        ),
        mapOf(
            "nome" to "Premiere 8",
            "url" to "https://embedcanais.com/premiere8",
            "img" to "https://embedcanais.com/images/premiere8.png",
            "category" to "esportes",
            "category_name" to "Esportes"
        ),
        mapOf(
            "nome" to "Record MG",
            "url" to "https://embedcanais.com/recordmg",
            "img" to "https://embedcanais.com/images/recordmg.png",
            "category" to "tv-aberta",
            "category_name" to "TV Aberta"
        ),
        mapOf(
            "nome" to "Record News",
            "url" to "https://embedcanais.com/recordnews",
            "img" to "https://embedcanais.com/images/recordnews.png",
            "category" to "noticias",
            "category_name" to "Notícias"
        ),
        mapOf(
            "nome" to "Record RJ",
            "url" to "https://embedcanais.com/recordrj",
            "img" to "https://embedcanais.com/images/recordrj.png",
            "category" to "tv-aberta",
            "category_name" to "TV Aberta"
        ),
        mapOf(
            "nome" to "Record SP",
            "url" to "https://embedcanais.com/recordsp",
            "img" to "https://embedcanais.com/images/recordsp.png",
            "category" to "tv-aberta",
            "category_name" to "TV Aberta"
        ),
        mapOf(
            "nome" to "Rede Gospel",
            "url" to "https://embedcanais.com/redegospel",
            "img" to "https://embedcanais.com/images/redegospel.png",
            "category" to "religiao",
            "category_name" to "Religião"
        ),
        mapOf(
            "nome" to "Rede Vida",
            "url" to "https://embedcanais.com/redevida",
            "img" to "https://embedcanais.com/images/redevida.png",
            "category" to "religiao",
            "category_name" to "Religião"
        ),
        mapOf(
            "nome" to "RedeTV!",
            "url" to "https://embedcanais.com/redetv",
            "img" to "https://embedcanais.com/images/redetv.png",
            "category" to "tv-aberta",
            "category_name" to "TV Aberta"
        ),
        mapOf(
            "nome" to "SBT SP",
            "url" to "https://embedcanais.com/sbtsp",
            "img" to "https://embedcanais.com/images/sbtsp.png",
            "category" to "tv-aberta",
            "category_name" to "TV Aberta"
        ),
        mapOf(
            "nome" to "Sony Channel",
            "url" to "https://embedcanais.com/sonychannel",
            "img" to "https://embedcanais.com/images/sonychannel.png",
            "category" to "entretenimento",
            "category_name" to "Entretenimento"
        ),
        mapOf(
            "nome" to "Space",
            "url" to "https://embedcanais.com/space",
            "img" to "https://embedcanais.com/images/space.png",
            "category" to "entretenimento",
            "category_name" to "Entretenimento"
        ),
        mapOf(
            "nome" to "SporTV",
            "url" to "https://embedcanais.com/sportv",
            "img" to "https://embedcanais.com/images/sportv.png",
            "category" to "esportes",
            "category_name" to "Esportes"
        ),
        mapOf(
            "nome" to "SporTV 2",
            "url" to "https://embedcanais.com/sportv2",
            "img" to "https://embedcanais.com/images/sportv2.png",
            "category" to "esportes",
            "category_name" to "Esportes"
        ),
        mapOf(
            "nome" to "SporTV 3",
            "url" to "https://embedcanais.com/sportv3",
            "img" to "https://embedcanais.com/images/sportv3.png",
            "category" to "esportes",
            "category_name" to "Esportes"
        ),
        mapOf(
            "nome" to "Star+",
            "url" to "https://embedcanais.com/starplus",
            "img" to "https://embedcanais.com/images/starplus.png",
            "category" to "streaming",
            "category_name" to "Streaming"
        ),
        mapOf(
            "nome" to "Studio Universal",
            "url" to "https://embedcanais.com/studiouniversal",
            "img" to "https://embedcanais.com/images/studiouniversal.png",
            "category" to "filmes",
            "category_name" to "Filmes"
        ),
        mapOf(
            "nome" to "Telecine Action",
            "url" to "https://embedcanais.com/tcaction",
            "img" to "https://embedcanais.com/images/tcaction.png",
            "category" to "filmes",
            "category_name" to "Filmes"
        ),
        mapOf(
            "nome" to "Telecine Cult",
            "url" to "https://embedcanais.com/tccult",
            "img" to "https://embedcanais.com/images/tccult.png",
            "category" to "filmes",
            "category_name" to "Filmes"
        ),
        mapOf(
            "nome" to "Telecine Fun",
            "url" to "https://embedcanais.com/tcfun",
            "img" to "https://embedcanais.com/images/tcfun.png",
            "category" to "filmes",
            "category_name" to "Filmes"
        ),
        mapOf(
            "nome" to "Telecine Pipoca",
            "url" to "https://embedcanais.com/tcpipoca",
            "img" to "https://embedcanais.com/images/tcpipoca.png",
            "category" to "filmes",
            "category_name" to "Filmes"
        ),
        mapOf(
            "nome" to "Telecine Premium",
            "url" to "https://embedcanais.com/tcpremium",
            "img" to "https://embedcanais.com/images/tcpremium.png",
            "category" to "filmes",
            "category_name" to "Filmes"
        ),
        mapOf(
            "nome" to "Telecine Touch",
            "url" to "https://embedcanais.com/tctouch",
            "img" to "https://embedcanais.com/images/tctouch.png",
            "category" to "filmes",
            "category_name" to "Filmes"
        ),
        mapOf(
            "nome" to "TNT",
            "url" to "https://embedcanais.com/tnt",
            "img" to "https://embedcanais.com/images/tnt.png",
            "category" to "entretenimento",
            "category_name" to "Entretenimento"
        ),
        mapOf(
            "nome" to "TNT Novelas",
            "url" to "https://embedcanais.com/tntnovelas",
            "img" to "https://embedcanais.com/images/tntnovelas.png",
            "category" to "entretenimento",
            "category_name" to "Entretenimento"
        ),
        mapOf(
            "nome" to "TNT Series",
            "url" to "https://embedcanais.com/tntseries",
            "img" to "https://embedcanais.com/images/tntseries.png",
            "category" to "entretenimento",
            "category_name" to "Entretenimento"
        ),
        mapOf(
            "nome" to "Tooncast",
            "url" to "https://embedcanais.com/tooncast",
            "img" to "https://embedcanais.com/images/tooncast.png",
            "category" to "infantil",
            "category_name" to "Infantil"
        ),
        mapOf(
            "nome" to "TV Aparecida",
            "url" to "https://embedcanais.com/tvaparecida",
            "img" to "https://embedcanais.com/images/tvaparecida.png",
            "category" to "religiao",
            "category_name" to "Religião"
        ),
        mapOf(
            "nome" to "TV Cultura",
            "url" to "https://embedcanais.com/tvcultura",
            "img" to "https://embedcanais.com/images/tvcultura.png",
            "category" to "cultura",
            "category_name" to "Cultura"
        ),
        mapOf(
            "nome" to "TV Evangelizar",
            "url" to "https://embedcanais.com/tvevangelizar",
            "img" to "https://embedcanais.com/images/tvevangelizar.png",
            "category" to "religiao",
            "category_name" to "Religião"
        ),
        mapOf(
            "nome" to "UFC Fight Pass",
            "url" to "https://embedcanais.com/ufcfightpass",
            "img" to "https://embedcanais.com/images/ufcfightpass.png",
            "category" to "esportes",
            "category_name" to "Esportes"
        ),
        mapOf(
            "nome" to "Universal TV",
            "url" to "https://embedcanais.com/universaltv",
            "img" to "https://embedcanais.com/images/universaltv.png",
            "category" to "entretenimento",
            "category_name" to "Entretenimento"
        ),
        mapOf(
            "nome" to "Warner Channel",
            "url" to "https://embedcanais.com/warner",
            "img" to "https://embedcanais.com/images/warner.png",
            "category" to "entretenimento",
            "category_name" to "Entretenimento"
        )
    )

    data class DataChannel(
        val name: String?,
        val img: String?,
        val category: String?,
        val url: String?,
        val categoryName: String?
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val homePages = channelList.asSequence()
            .map { map ->
                DataChannel(
                    name = map["nome"],
                    img = map["img"],
                    category = map["category"],
                    url = map["url"],
                    categoryName = map["category_name"]
                )
            }.groupBy { it.category ?: "Outros" }
            .map { (_, canais) ->
                val list = canais.map { item ->
                    newLiveSearchResponse(item.name ?: "", item.url ?: "", TvType.Live) {
                        this.posterUrl = item.img
                    }
                }
                // Usa categoryName do primeiro canal como título
                HomePageList(canais.firstOrNull()?.categoryName ?: "Outros", list)
            }

        return newHomePageResponse(homePages)
    }

    override suspend fun load(data: String): LoadResponse {
        val channelUrl = data.ifEmpty { throw ErrorLoadingException("Invalid Json reponse") }

        val channel = channelList.find { it["url"] == channelUrl }

        return newMovieLoadResponse(
            name = channel?.get("nome") ?: "Canal",
            url = channelUrl,
            type = TvType.Live,
            channelUrl
        ) {
            this.posterUrl = channel?.get("img")
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val channelUrl = data.ifEmpty { return false }
        val doc = app.get(channelUrl).document

        val scriptContent = doc.select("script").firstOrNull { script ->
            script.data().contains("source:") && script.data().contains(".m3u8")
        }?.data() ?: return false

        Regex("source:\\s*\"([^\"]+\\.m3u8[^\"]*)\"").find(scriptContent)?.groupValues?.get(1)
            ?.let { url ->
                callback(newExtractorLink("EmbedCanais", "EmbedCanais Live", url) {
                    this.referer = channelUrl
                })
                return true
            }

        Regex("embmaxtv\\.online/[^/]+/index\\.m3u8").find(scriptContent)?.value?.let { path ->
            val url = "https://$path"
            callback(newExtractorLink("EmbedCanais", "EmbedCanais Live", url) {
                this.referer = channelUrl
            })
            return true
        }

        return false
    }

}