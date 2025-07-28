package com.EmbedCanais

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import kotlinx.coroutines.delay
import org.json.JSONObject

class EmbedCanais : MainAPI() {
    override var mainUrl = "https://embedcanaistv.com"
    override var name = "EmbedCanais"
    override val hasMainPage = true
    override var lang = "pt-br"
    override val hasDownloadSupport = false
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.Live)

    private var postersCache: Map<String, String> = emptyMap()


    private val POSTERS_JSON = """
    {
      "canais": {
        "esportes": [
          {
            "nome": "Amazon Prime Video",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/Prime_Video.png",
            "url": "https://embedcanaistv.com/amazonprimevideo"
          },
          {
            "nome": "Band Sports",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/EuPvzJe.png",
            "url": "https://embedcanaistv.com/bandsports/"
          },
          {
            "nome": "Canal GOAT",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/F_Jf0NdWAAAa2Ld.png",
            "url": "https://embedcanaistv.com/canalgoat/"
          },
          {
            "nome": "Combate",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/07/Canal-combate-logo-scaled.png",
            "url": "https://embedcanaistv.com/combate/"
          },
          {
            "nome": "Cazé TV",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/Perfil-casimito.webp",
            "url": "https://embedcanaistv.com/cazetv/"
          },
          {
            "nome": "DAZN",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/DAZN_BoxedLogo_01_RGB.png",
            "url": "https://embedcanaistv.com/dazn/"
          },
          {
            "nome": "Disney +",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/logo-disneyplus-1024.png",
            "url": "https://embedcanaistv.com/disneyplus/"
          },
          {
            "nome": "ESPN",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/ESPN_wordmark.svg_.png",
            "url": "https://embedcanaistv.com/espn/"
          },
          {
            "nome": "ESPN 2",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/ESPN_wordmark.svg_.png",
            "url": "https://embedcanaistv.com/espn2/"
          },
          {
            "nome": "ESPN 3",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/ESPN_wordmark.svg_.png",
            "url": "https://embedcanaistv.com/espn3/"
          },
          {
            "nome": "ESPN 4",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/ESPN_wordmark.svg_.png",
            "url": "https://embedcanaistv.com/espn4/"
          },
          {
            "nome": "ESPN 5",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/ESPN_wordmark.svg_.png",
            "url": "https://embedcanaistv.com/espn5/"
          },
          {
            "nome": "ESPN 6",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/ESPN_wordmark.svg_.png",
            "url": "https://embedcanaistv.com/espn6/"
          },
          {
            "nome": "Max",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/Max_logo.svg_.png",
            "url": "https://embedcanaistv.com/max/"
          },
          {
            "nome": "Nosso Futebol",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/07/nossofutebol.png",
            "url": "https://embedcanaistv.com/nossofutebol"
          },
          {
            "nome": "Paramount +",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/paramount-plus-logo-0-scaled.png",
            "url": "https://embedcanaistv.com/paramountplus"
          },
          {
            "nome": "Premiere Clubes",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/premiere.png",
            "url": "https://embedcanaistv.com/premiereclubes"
          },
          {
            "nome": "Premiere 2",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/premiere.png",
            "url": "https://embedcanaistv.com/premiere2"
          },
          {
            "nome": "Premiere 3",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/premiere.png",
            "url": "https://embedcanaistv.com/premiere3"
          },
          {
            "nome": "Premiere 4",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/premiere.png",
            "url": "https://embedcanaistv.com/premiere4"
          },
          {
            "nome": "Premiere 5",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/premiere.png",
            "url": "https://embedcanaistv.com/premiere5"
          },
          {
            "nome": "Premiere 6",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/premiere.png",
            "url": "https://embedcanaistv.com/premiere6"
          },
          {
            "nome": "Premiere 7",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/premiere.png",
            "url": "https://embedcanaistv.com/premiere7"
          },
          {
            "nome": "Premiere 8",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/premiere.png",
            "url": "https://embedcanaistv.com/premiere8"
          },
          {
            "nome": "SportTV Portugal 1",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/Sport_TV1_2023.png",
            "url": "https://embedcanaistv.com/sporttvportugal1"
          },
          {
            "nome": "SportTV Portugal 2",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/Sport_TV2_2023.png",
            "url": "https://embedcanaistv.com/sporttvportugal2"
          },
          {
            "nome": "SportTV Portugal 3",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/Sport_TV3_2023.png",
            "url": "https://embedcanaistv.com/sporttvportugal3"
          },
          {
            "nome": "SportTV Portugal 4",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/Sport_TV4_2023.png",
            "url": "https://embedcanaistv.com/sporttvportugal4"
          },
          {
            "nome": "SportTV Portugal 5",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/Sport_TV5_2023.png",
            "url": "https://embedcanaistv.com/sporttvportugal5"
          },
          {
            "nome": "Sportv",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/07/SporTV.png",
            "url": "https://embedcanaistv.com/sportv"
          },
          {
            "nome": "Sportv 2",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/07/SporTV2.png",
            "url": "https://embedcanaistv.com/sportv2"
          },
          {
            "nome": "Sportv 3",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/07/SporTV3.png",
            "url": "https://embedcanaistv.com/sportv3"
          },
          {
            "nome": "Star +",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/Star_logo.svg_.png",
            "url": "https://embedcanaistv.com/starplus"
          },
          {
            "nome": "TNT",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/07/kindpng_5420951.png",
            "url": "https://embedcanaistv.com/tnt"
          },
          {
            "nome": "UFC Fight Pass",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/UFC_Fight_Pass_Logo.svg_.png",
            "url": "https://embedcanaistv.com/ufcfightpass"
          }
        ],
        "abertos": [
          {
            "nome": "Band SP",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/band.png",
            "url": "https://embedcanaistv.com/bandsp/"
          },
          {
            "nome": "Globo MG",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/TVGlobo2025.webp",
            "url": "https://embedcanaistv.com/globomg/"
          },
          {
            "nome": "Globo RJ",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/TVGlobo2025.webp",
            "url": "https://embedcanaistv.com/globorj/"
          },
          {
            "nome": "Globo RS",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/TVGlobo2025.webp",
            "url": "https://embedcanaistv.com/globors/"
          },
          {
            "nome": "Globo SP",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/TVGlobo2025.webp",
            "url": "https://embedcanaistv.com/globosp/"
          },
          {
            "nome": "Record RJ",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/record.png",
            "url": "https://embedcanaistv.com/recordrj"
          },
          {
            "nome": "Record SP",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/record.png",
            "url": "https://embedcanaistv.com/recordsp"
          },
          {
            "nome": "Rede Tv",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/redetv.png",
            "url": "https://embedcanaistv.com/redetv"
          },
          {
            "nome": "Rede Vida",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/mP1qfp2.png",
            "url": "https://embedcanaistv.com/redevida"
          },
          {
            "nome": "SBT SP",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/sbt.png",
            "url": "https://embedcanaistv.com/sbtsp"
          },
          {
            "nome": "TV Cultura",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/hcApLfE.png",
            "url": "https://embedcanaistv.com/tvcultura"
          }
        ],
        "filmes_series": [
          {
            "nome": "A&E",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/07/pngfind.com-as-seen-on-tv-4187219.png",
            "url": "https://embedcanaistv.com/aee/"
          },
          {
            "nome": "Adult Swim",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/07/7xxuyo.webp",
            "url": "https://embedcanaistv.com/adultswim/"
          },
          {
            "nome": "AMC",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/07/pngfind.com-amc-logo-png-1449958.png",
            "url": "https://embedcanaistv.com/amc/"
          },
          {
            "nome": "AXN",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/AXN_logo_2015.svg_.png",
            "url": "https://embedcanaistv.com/axn/"
          },
          {
            "nome": "Cinecanal",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/CinecanalLA.png",
            "url": "https://embedcanaistv.com/cinecanal/"
          },
          {
            "nome": "Cinemax",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/Cinemax_Yellow.svg_.png",
            "url": "https://embedcanaistv.com/cinemax/"
          },
          {
            "nome": "Darkflix",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/Logo_do_Darkflix.png",
            "url": "https://embedcanaistv.com/darkflix/"
          },
          {
            "nome": "Film&Arts Brasil",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/2kHUsl7.png",
            "url": "https://embedcanaistv.com/filmarts/"
          },
          {
            "nome": "HBO",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/07/Daco_4997049.png",
            "url": "https://embedcanaistv.com/hbo/"
          },
          {
            "nome": "HBO 2",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/07/HBO_2.png",
            "url": "https://embedcanaistv.com/hbo2/"
          },
          {
            "nome": "HBO Family",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/07/HBO_FAMILY.png",
            "url": "https://embedcanaistv.com/hbofamily/"
          },
          {
            "nome": "HBO Mundi",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/07/HBO_MUNDI.png",
            "url": "https://embedcanaistv.com/hbomundi/"
          },
          {
            "nome": "HBO Plus",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/07/HBO_PLUS.png",
            "url": "https://embedcanaistv.com/hboplus/"
          },
          {
            "nome": "HBO Pop",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/07/HBO_POP.png",
            "url": "https://embedcanaistv.com/hbopop/"
          },
          {
            "nome": "HBO Signature",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/07/HBO_SIGNATURE.png",
            "url": "https://embedcanaistv.com/hbosignature/"
          },
          {
            "nome": "HBO Xtreme",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/07/HBO_XTREME.png",
            "url": "https://embedcanaistv.com/hboxtreme/"
          },
          {
            "nome": "Megapix",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/07/megapix-logo-8-scaled.png",
            "url": "https://embedcanaistv.com/megapix/"
          },
          {
            "nome": "Sony Channel",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/512px-Sony_Channel_Logo.png",
            "url": "https://embedcanaistv.com/sonychannel"
          },
          {
            "nome": "Space",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/SpaceLogo.svg_.png",
            "url": "https://embedcanaistv.com/space"
          },
          {
            "nome": "Star Channel",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/Star_Channel_2020.svg_.png",
            "url": "https://embedcanaistv.com/starchannel"
          },
          {
            "nome": "Studio Universal",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/07/StudioUniversal.png",
            "url": "https://embedcanaistv.com/studiouniversal"
          },
          {
            "nome": "Telecine Action",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/07/tcaction.png",
            "url": "https://embedcanaistv.com/tcaction"
          },
          {
            "nome": "Telecine Cult",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/07/tcult.png",
            "url": "https://embedcanaistv.com/tccult"
          },
          {
            "nome": "Telecine Fun",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/07/tcfun.png",
            "url": "https://embedcanaistv.com/tcfun"
          },
          {
            "nome": "Telecine Pipoca",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/07/tcpipoca.png",
            "url": "https://embedcanaistv.com/tcpipoca"
          },
          {
            "nome": "Telecine Premium",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/07/tcpremium.png",
            "url": "https://embedcanaistv.com/tcpremium"
          },
          {
            "nome": "Telecine Touch",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/07/tctouch.png",
            "url": "https://embedcanaistv.com/tctouch"
          },
          {
            "nome": "TNT",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/07/kindpng_5420951.png",
            "url": "https://embedcanaistv.com/tnt"
          },
          {
            "nome": "TNT Novelas",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/Logo_TNT_Novelas.png",
            "url": "https://embedcanaistv.com/tntnovelas"
          },
          {
            "nome": "TNT Séries",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/TNT_Series_Logo_2016.png",
            "url": "https://embedcanaistv.com/tntseries"
          },
          {
            "nome": "Universal Tv",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/Jt0B1Ac.png",
            "url": "https://embedcanaistv.com/universaltv"
          },
          {
            "nome": "Warner Tv",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/Warner2018LA.png",
            "url": "https://embedcanaistv.com/warner"
          }
        ],
        "variedades": [
          {
            "nome": "Animal Planet",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/2018_Animal_Planet_logo.svg_.png",
            "url": "https://embedcanaistv.com/animalplanet/"
          },
          {
            "nome": "ARTE 1",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/Ivnk7A6.png",
            "url": "https://embedcanaistv.com/arte1/"
          },
          {
            "nome": "Canal Brasil",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/CanalBrasil_logos_700px_0-03.png",
            "url": "https://embedcanaistv.com/canalbrasil/"
          },
          {
            "nome": "Comedy Central",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/Comedy_Central_2018.svg-1.png",
            "url": "https://embedcanaistv.com/comedycentral/"
          },
          {
            "nome": "Discovery Channel",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/07/disco.png",
            "url": "https://embedcanaistv.com/discoverychannel/"
          },
          {
            "nome": "Discovery Home & Health",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/Discovery_HH_Logo_2022.webp.png",
            "url": "https://embedcanaistv.com/discoveryhh/"
          },
          {
            "nome": "Discovery Science",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/Discovery_science.png",
            "url": "https://embedcanaistv.com/discoveryscience/"
          },
          {
            "nome": "Discovery Theater",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/T07gvEb.png",
            "url": "https://embedcanaistv.com/discoverytheater/"
          },
          {
            "nome": "Discovery World",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/Yb3oUls.png",
            "url": "https://embedcanaistv.com/discoveryworld/"
          },
          {
            "nome": "Discovey ID",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/pngaaa.com-2898901.png",
            "url": "https://embedcanaistv.com/discoveryid/"
          },
          {
            "nome": "Discovey Turbo",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/WuOhLl4.png",
            "url": "https://embedcanaistv.com/discoveryturbo/"
          },
          {
            "nome": "Film&Arts Brasil",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/2kHUsl7.png",
            "url": "https://embedcanaistv.com/filmarts/"
          },
          {
            "nome": "Fish TV",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/Fish_TV_logo_2022.svg_.png",
            "url": "https://embedcanaistv.com/fishtv/"
          },
          {
            "nome": "Food Network",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/6l7us9Y.png",
            "url": "https://embedcanaistv.com/foodnetwork/"
          },
          {
            "nome": "Fuel TV",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/Fuel_TV.svg_.png",
            "url": "https://embedcanaistv.com/fueltv/"
          },
          {
            "nome": "GNT",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/GNT_logo-roxo.svg_.png",
            "url": "https://embedcanaistv.com/gnt/"
          },
          {
            "nome": "HGTV",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/HGTV_US_Logo_2015.svg_.png",
            "url": "https://embedcanaistv.com/hgtv/"
          },
          {
            "nome": "History",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/History_2021.svg_.png",
            "url": "https://embedcanaistv.com/history/"
          },
          {
            "nome": "History 2",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/History2_logo_2022.svg_.png",
            "url": "https://embedcanaistv.com/history2/"
          },
          {
            "nome": "LifeTime",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/Logo_Lifetime_2020.svg_.png",
            "url": "https://embedcanaistv.com/lifetime/"
          },
          {
            "nome": "Multishow",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/bqVg8nT.png",
            "url": "https://embedcanaistv.com/multishow/"
          },
          {
            "nome": "MTV",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/MTV_2021_brand_version.svg_.png",
            "url": "https://embedcanaistv.com/mtv/"
          },
          {
            "nome": "National Geographic",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/Natgeologo.svg_.png",
            "url": "https://embedcanaistv.com/nationalgeografic/"
          },
          {
            "nome": "TLC",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/TLC_Logo.svg_.png",
            "url": "https://embedcanaistv.com/tlc"
          },
          {
            "nome": "VIVA",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/Canal_Viva_2018_wordmark.svg_.png",
            "url": "https://embedcanaistv.com/viva"
          }
        ],
        "noticias": [
          {
            "nome": "Band News",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/3YlJsCL.png",
            "url": "https://embedcanaistv.com/bandnews"
          },
          {
            "nome": "CNN Brasil",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/CNN_Brasil.svg_.png",
            "url": "https://embedcanaistv.com/cnnbrasil/"
          },
          {
            "nome": "Globo News",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/3yZyaCm.png",
            "url": "https://embedcanaistv.com/globonews/"
          },
          {
            "nome": "Record News",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/HZDRG0K.png",
            "url": "https://embedcanaistv.com/recordnews"
          }
        ],
        "infantil": [
          {
            "nome": "Cartoon Network",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/Cartoon_Network_2010_logo.svg_.png",
            "url": "https://embedcanaistv.com/cartoonnetwork/"
          },
          {
            "nome": "Cartoonito",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/Cartoonito_-_Logo_2021.svg_.png",
            "url": "https://embedcanaistv.com/cartoonito/"
          },
          {
            "nome": "Discovery Kids",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/C0VEHXU.png",
            "url": "https://embedcanaistv.com/discoverykids/"
          },
          {
            "nome": "Disney Channel",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/Disney_channel_2019.png",
            "url": "https://embedcanaistv.com/disneychannel/"
          },
          {
            "nome": "DreamWorks Channel",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/Dreamworks_Channel_29.webp",
            "url": "https://embedcanaistv.com/dreamworkschannel/"
          },
          {
            "nome": "Gloob",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/N1BUULh.png",
            "url": "https://embedcanaistv.com/gloob/"
          },
          {
            "nome": "Gloobinho",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/UWkHErt.png",
            "url": "https://embedcanaistv.com/gloobinho/"
          },
          {
            "nome": "Nick Jr.",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/Nick_Jr._logo_2009.svg_.png",
            "url": "https://embedcanaistv.com/nickjr/"
          },
          {
            "nome": "Nickelodeon",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/Nickelodeon_2009_logo.svg_.png",
            "url": "https://embedcanaistv.com/nickelodeon"
          },
          {
            "nome": "Tooncast",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/Tooncast_logo.svg_.png",
            "url": "https://embedcanaistv.com/tooncast"
          },
          {
            "nome": "TV Rá Tim Bum",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/TV_Ra-Tim-Bum.png",
            "url": "https://embedcanaistv.com/tvratimbum"
          },
          {
            "nome": "ZooMoo Kids",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/cropped-zoomoo-kids-logo.webp",
            "url": "https://embedcanaistv.com/zoomookids"
          }
        ],
        "bonus": [
          {
            "nome": "Hallo Anime",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/ANIME.png",
            "url": "https://embedcanaistv.com/halloanime"
          },
          {
            "nome": "Hallo Classic",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/CLASSIC.png",
            "url": "https://embedcanaistv.com/halloclassic"
          },
          {
            "nome": "Hallo Doc",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/DOC.png",
            "url": "https://embedcanaistv.com/hallodoc"
          },
          {
            "nome": "Hallo Dorama",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/DORAMA.png",
            "url": "https://embedcanaistv.com/hallodorama"
          },
          {
            "nome": "Hallo Movies",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/MOVIES.png",
            "url": "https://embedcanaistv.com/hallomovies"
          },
          {
            "nome": "Hallo Series",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/SERIES.png",
            "url": "https://embedcanaistv.com/halloseries"
          },
          {
            "nome": "Chapolin",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/spenelk5NkW3jSpfJ4TR9JdT3hZ.webp",
            "url": "https://embedcanaistv.com/24h-chapolin"
          },
          {
            "nome": "Chaves",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/502374.png",
            "url": "https://embedcanaistv.com/24h-chaves"
          },
          {
            "nome": "Dragon Ball Z",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/Dragon_Ball_Z_Logo_A.png",
            "url": "https://embedcanaistv.com/24h-dragonballz"
          },
          {
            "nome": "Naruto Shippuden",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/Logo_Naruto_Shippuden.png",
            "url": "https://embedcanaistv.com/24h-naruto"
          },
          {
            "nome": "Os Cavaleiros do Zodiaco",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/cavaleiros-do-zodiaco-logo-png_seeklogo-239247.png",
            "url": "https://embedcanaistv.com/24h-oscavaleirosdozodiaco"
          },
          {
            "nome": "Os Simpsons",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/The_Simpsons_yellow_logo.svg_.png",
            "url": "https://embedcanaistv.com/24h-simpsons"
          },
          {
            "nome": "South Park",
            "poster": "https://embedcanaistv.com/wp-content/uploads/2025/06/South-Park-Logo-PNG-Image.png",
            "url": "https://embedcanaistv.com/24h-southpark"
          }
        ]
      },
      "total_canais": 108,
      "categorias": {
        "esportes": 33,
        "abertos": 11,
        "filmes_series": 30,
        "variedades": 25,
        "noticias": 4,
        "infantil": 12,
        "bonus": 13
      }
    }
    """.trimIndent()

    override val mainPage = mainPageOf(
        "esportes" to "Esportes",
        "abertos" to "Canais Abertos",
        "filmes" to "Filmes e Séries",
        "variedades" to "Variedades",
        "noticias" to "Notícias",
        "infantil" to "Infantil/Desenhos",
        "bonus" to "Bonus"
    )

    private fun loadPostersFromJson(): Map<String, String> {
        if (postersCache.isNotEmpty()) {
            return postersCache
        }

        try {
            val jsonObject = JSONObject(POSTERS_JSON)
            val canais = jsonObject.getJSONObject("canais")
            val posters = mutableMapOf<String, String>()
            
            val categorias = listOf("esportes", "abertos", "filmes_series", "variedades", "noticias", "infantil", "bonus")
            
            categorias.forEach { categoria ->
                if (canais.has(categoria)) {
                    val categoriaArray = canais.getJSONArray(categoria)
                    for (i in 0 until categoriaArray.length()) {
                        val canal = categoriaArray.getJSONObject(i)
                        val nome = canal.getString("nome")
                        val poster = canal.getString("poster")
                        posters[nome] = poster
                    }
                }
            }
            
            postersCache = posters
            return posters
            
        } catch (e: Exception) {
            return emptyMap()
        }
    }

    private fun getPosterForChannel(channelName: String): String {
        val posters = loadPostersFromJson()
        val poster = posters[channelName] ?: ""
        
        return if (poster.isNotEmpty()) {
            poster
        } else {
            "https://embedcanaistv.com/wp-content/uploads/2024/11/cropped-cropped-iconn-192x192.png"
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = "$mainUrl"
        val response = app.get(url)
        val doc = response.document
        
        val channels = mutableListOf<SearchResponse>()

        val categorySelector = when (request.data) {
            "esportes" -> "h4:contains(Esportes) + .grid-container .grid-item"
            "abertos" -> "h4:contains(Canais Abertos) + .grid-container .grid-item"
            "filmes" -> "h4:contains(Filmes e Séries) + .grid-container .grid-item"
            "variedades" -> "h4:contains(Variedades) + .grid-container .grid-item"
            "noticias" -> "h4:contains(Notícias) + .grid-container .grid-item"
            "infantil" -> "h4:contains(Infantil/Desenhos) + .grid-container .grid-item"
            "bonus" -> "h4:contains(Bonus) + .grid-container .grid-item"
            else -> ".grid-container .grid-item"
        }

        val selectedChannels = doc.select(categorySelector)

        selectedChannels.forEach { channel ->
            val title = channel.selectFirst("h3")?.text()
            val channelUrl = channel.selectFirst("a")?.attr("href")
            
            if (title != null && channelUrl != null) {
                val posterUrl = getPosterForChannel(title)
                val fullChannelUrl = if (channelUrl.startsWith("http")) channelUrl else "$mainUrl$channelUrl"
                
                channels.add(
                    newTvSeriesSearchResponse(title, fullChannelUrl, TvType.Live) {
                        this.posterUrl = posterUrl
                    }
                )
            }
        }

        return newHomePageResponse(HomePageList(request.name, channels))
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl"
        val response = app.get(url)
        val doc = response.document
        
        val results = mutableListOf<SearchResponse>()

        val allChannels = doc.select(".grid-container .grid-item")

        allChannels.forEach { channel ->
            val title = channel.selectFirst("h3")?.text()
            val channelUrl = channel.selectFirst("a")?.attr("href")

            if (title != null && channelUrl != null && title.contains(query, ignoreCase = true)) {
                val posterUrl = getPosterForChannel(title)
                val fullChannelUrl = if (channelUrl.startsWith("http")) channelUrl else "$mainUrl$channelUrl"
                
                results.add(
                    newTvSeriesSearchResponse(title, fullChannelUrl, TvType.Live) {
                        this.posterUrl = posterUrl
                    }
                )
            }
        }

        return results
    }

    override suspend fun load(url: String): LoadResponse {
        val response = app.get(url)
        val doc = response.document
        
        val title = doc.selectFirst("h1")?.text() ?: doc.title()
        val posterUrl = getPosterForChannel(title)

        return newMovieLoadResponse(title, url, TvType.Live, url) {
            this.posterUrl = posterUrl
            this.plot = "Canal ao vivo - $title"
            this.dataUrl = url
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val channelUrl = if (data.isNotEmpty()) data else return false
        
        val response = app.get(channelUrl)
        val doc = response.document
        
        val scripts = doc.select("script")
        
        val scriptContent = scripts.find { script ->
            script.data().contains("source:") && script.data().contains(".m3u8")
        }?.data()
        
        if (scriptContent == null) {
            return false
        }

        val m3u8Regex = Regex("source:\\s*\"([^\"]+\\.m3u8[^\"]*)\"")
        val m3u8Match = m3u8Regex.find(scriptContent)
        
        if (m3u8Match != null) {
            val m3u8Url = m3u8Match.groupValues[1]
            
            callback(
                newExtractorLink(
                    "EmbedCanais",
                    "EmbedCanais Live",
                    m3u8Url
                ) {
                    this.referer = channelUrl
                    this.headers = mapOf(
                        "accept" to "*/*",
                        "accept-encoding" to "gzip, deflate, br, zstd",
                        "accept-language" to "pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7",
                        "cache-control" to "no-cache",
                        "dnt" to "1",
                        "origin" to "https://embedcanaistv.com",
                        "pragma" to "no-cache",
                        "referer" to "https://embedcanaistv.com/",
                        "sec-ch-ua" to "\"Google Chrome\";v=\"137\", \"Chromium\";v=\"137\", \"Not/A)Brand\";v=\"24\"",
                        "sec-ch-ua-mobile" to "?0",
                        "sec-ch-ua-platform" to "\"Windows\"",
                        "sec-fetch-dest" to "empty",
                        "sec-fetch-mode" to "cors",
                        "sec-fetch-site" to "cross-site",
                        "sec-gpc" to "1",
                        "user-agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36"
                    )
                }
            )
            return true
        }

        val alternativeRegex = Regex("embmaxtv\\.online/[^/]+/index\\.m3u8")
        val alternativeMatch = alternativeRegex.find(scriptContent)
        
        if (alternativeMatch != null) {
            val m3u8Url = "https://" + alternativeMatch.value
            
            callback(
                newExtractorLink(
                    "EmbedCanais",
                    "EmbedCanais Live",
                    m3u8Url
                ) {
                    this.referer = channelUrl
                    this.headers = mapOf(
                        "accept" to "*/*",
                        "accept-encoding" to "gzip, deflate, br, zstd",
                        "accept-language" to "pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7",
                        "cache-control" to "no-cache",
                        "dnt" to "1",
                        "origin" to "https://embedcanaistv.com",
                        "pragma" to "no-cache",
                        "referer" to "https://embedcanaistv.com/",
                        "sec-ch-ua" to "\"Google Chrome\";v=\"137\", \"Chromium\";v=\"137\", \"Not/A)Brand\";v=\"24\"",
                        "sec-ch-ua-mobile" to "?0",
                        "sec-ch-ua-platform" to "\"Windows\"",
                        "sec-fetch-dest" to "empty",
                        "sec-fetch-mode" to "cors",
                        "sec-fetch-site" to "cross-site",
                        "sec-gpc" to "1",
                        "user-agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36"
                    )
                }
            )
            return true
        }

        return false
    }
} 