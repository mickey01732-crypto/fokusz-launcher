package hu.miki.fokusz

import java.util.Calendar

data class Saying(val jp: String, val reading: String, val hu: String, val note: String)

object Sayings {
    val ALL = listOf(
        Saying("一期一会", "ichigo ichie", "Minden találkozás egyszeri.", "Ez a pillanat így soha többé nem ismétlődik, ezért adj bele mindent."),
        Saying("七転び八起き", "nana korobi ya oki", "Hétszer elesel, nyolcszor felállsz.", "A kitartás nem a hibátlanság, hanem a felállás."),
        Saying("初心", "shoshin", "Kezdő elme.", "Nyitottan közelíts, mintha először csinálnád, a tapasztalat ne zárja be a szemed."),
        Saying("残心", "zanshin", "A megmaradó figyelem.", "A vágás nem ér véget a találatnál: a mozdulat után is jelen maradsz."),
        Saying("守破離", "shu-ha-ri", "Megtartani, megtörni, elszakadni.", "Előbb a formát tanulod meg, aztán túllépsz rajta, végül a magadévá válik."),
        Saying("改善", "kaizen", "Folyamatos jobbítás.", "Nem nagy ugrások, hanem apró lépések, minden nap."),
        Saying("生き甲斐", "ikigai", "Amiért érdemes felkelni.", "Ahol az, amit szeretsz, amiben jó vagy és amire szükség van, találkozik."),
        Saying("侘寂", "wabi-sabi", "A tökéletlen szépsége.", "Az egyszerű, a mulandó és a befejezetlen is lehet szép."),
        Saying("金継ぎ", "kintsugi", "Aranyjavítás.", "A törés a történet része, nem eltakarni kell, hanem arannyal kiemelni."),
        Saying("無心", "mushin", "Üres elme.", "Félelem és ítélkezés nélkül cselekszel, a gondolat nem akad meg semmin."),
        Saying("不動心", "fudōshin", "Mozdíthatatlan szív.", "Nyugodt maradsz, bármi történik körülötted."),
        Saying("平常心", "heijōshin", "Hétköznapi szív.", "Ugyanazzal a nyugalommal lépsz a nagy pillanatba, mint ahogy teát iszol."),
        Saying("心技体", "shin-gi-tai", "Szív, technika, test.", "A három csak együtt működik; ha egy hiányzik, a másik kettő sem elég."),
        Saying("稽古", "keiko", "Gyakorlás.", "Nem az egyszeri teljesítmény formál, hanem az ismétlés."),
        Saying("礼", "rei", "Tisztelet.", "A gyakorlás meghajlással kezdődik és azzal ér véget."),
        Saying("一意専心", "ichii senshin", "Egy cél, osztatlan figyelem.", "Egyszerre egy dologra figyelsz, de arra teljesen."),
        Saying("一所懸命", "isshokenmei", "Teljes odaadással.", "Amit csinálsz, azt egész szívvel csináld."),
        Saying("明鏡止水", "meikyō shisui", "Tiszta tükör, álló víz.", "A nyugodt elme tisztán látja a dolgokat."),
        Saying("行雲流水", "kōun ryūsui", "Úszó felhő, folyó víz.", "Ragaszkodás nélkül haladsz tovább, ahogy a helyzet kívánja."),
        Saying("日々是好日", "nichinichi kore kōjitsu", "Minden nap jó nap.", "Nem a nap a jó vagy rossz, hanem az, ahogyan éled."),
        Saying("間", "ma", "A köztes tér.", "A szünet is része a zenének, a csend is része a beszélgetésnek."),
        Saying("和", "wa", "Harmónia.", "A csapat ereje az összhangban van, nem az egyéni csillogásban."),
        Saying("継続は力なり", "keizoku wa chikara nari", "A kitartás erő.", "Ami folyamatos, az halmozódik."),
        Saying("千里の道も一歩から", "senri no michi mo ippo kara", "Az ezer mérföldes út is egy lépéssel kezdődik.", "Ne a táv ijesszen meg, hanem a következő lépésre figyelj."),
        Saying("塵も積もれば山となる", "chiri mo tsumoreba yama to naru", "A porszemekből is hegy lesz.", "Az apró, ismételt dolgok idővel hatalmasra nőnek."),
        Saying("石の上にも三年", "ishi no ue ni mo sannen", "Három év a kövön is.", "Türelemmel még a hideg kő is felmelegszik."),
        Saying("急がば回れ", "isogaba maware", "Ha sietsz, kerülj.", "A biztos út gyakran gyorsabb, mint a kockázatos rövidítés."),
        Saying("猿も木から落ちる", "saru mo ki kara ochiru", "A majom is leesik a fáról.", "A mester is hibázik, ez nem a vége, csak egy esés."),
        Saying("雨降って地固まる", "ame futte ji katamaru", "Eső után megkeményedik a föld.", "A nehézség után szilárdabb lesz, ami megmarad."),
        Saying("案ずるより産むが易し", "anzuru yori umu ga yasushi", "Megtenni könnyebb, mint aggódni miatta.", "A félelem nagyobb, mint maga a feladat."),
        Saying("十人十色", "jūnin toiro", "Tíz ember, tíz szín.", "Mindenki más, és mindenkit a saját nyelvén lehet elérni."),
        Saying("腹八分目", "hara hachi-bu", "Csak nyolc tizedig.", "Ne töltsd meg magad teljesen: se a tányért, se a naptárat."),
        Saying("足るを知る", "taru o shiru", "Tudni, mi elég.", "Aki tudja, mi elég, az gazdag."),
        Saying("段取り八分", "dandori hachi-bu", "Az előkészítés a munka nyolc tizede.", "A jól előkészített munka már félig kész."),
        Saying("一日一歩", "ichinichi ippo", "Naponta egy lépés.", "Nem kell ma befejezni, csak ma is haladni."),
        Saying("一日一善", "ichinichi ichizen", "Minden nap egy jó cselekedet.", "Egy kis jóság naponta többet ér, mint egy nagy egyszer."),
        Saying("温故知新", "onko chishin", "A régit tanulmányozva ismered meg az újat.", "A múlt tapasztalata a jövő iránytűje."),
        Saying("能ある鷹は爪を隠す", "nō aru taka wa tsume o kakusu", "Az ügyes sólyom elrejti a karmait.", "Az igazi tudás nem kérkedik."),
        Saying("鉄は熱いうちに打て", "tetsu wa atsui uchi ni ute", "Üsd a vasat, amíg meleg.", "A lehetőség nem vár, cselekedj, amikor itt az ideje."),
        Saying("備えあれば憂いなし", "sonae areba urei nashi", "Ha felkészültél, nincs mitől tartanod.", "A nyugalom a felkészülésből születik."),
        Saying("笑う門には福来る", "warau kado ni wa fuku kitaru", "A nevető kapun belép a szerencse.", "A derű vonzza a jót."),
        Saying("覚悟", "kakugo", "Elszántság.", "Amikor eldöntötted, már nem kérdezed, csak csinálod."),
        Saying("自分に勝つ", "jibun ni katsu", "Legyőzni önmagad.", "Az igazi ellenfél a saját kényelmed."),
        Saying("道", "dō", "Az út.", "Nem a cél a lényeg, hanem az, ahogyan haladsz."),
    )

    fun today(): Saying {
        val d = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        return ALL[d % ALL.size]
    }
}
