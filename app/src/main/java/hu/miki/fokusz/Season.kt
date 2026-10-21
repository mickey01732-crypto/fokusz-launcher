package hu.miki.fokusz

import java.util.Calendar

data class Sekki(val month: Int, val day: Int, val jp: String, val reading: String, val hu: String)

/** A japán naptár 24 évszaka (sekki), hozzávetőleges kezdőnapokkal. */
object Season {
    private val ALL = listOf(
        Sekki(1, 6, "小寒", "shōkan", "Kis hideg"),
        Sekki(1, 20, "大寒", "daikan", "Nagy hideg"),
        Sekki(2, 4, "立春", "risshun", "Tavasz kezdete"),
        Sekki(2, 19, "雨水", "usui", "Esővíz"),
        Sekki(3, 6, "啓蟄", "keichitsu", "Ébredő rovarok"),
        Sekki(3, 21, "春分", "shunbun", "Tavaszi napéjegyenlőség"),
        Sekki(4, 5, "清明", "seimei", "Tiszta fény"),
        Sekki(4, 20, "穀雨", "kokuu", "Gabonaeső"),
        Sekki(5, 6, "立夏", "rikka", "Nyár kezdete"),
        Sekki(5, 21, "小満", "shōman", "Kis bőség"),
        Sekki(6, 6, "芒種", "bōshu", "Kalászos vetés"),
        Sekki(6, 21, "夏至", "geshi", "Nyári napforduló"),
        Sekki(7, 7, "小暑", "shōsho", "Kis hőség"),
        Sekki(7, 23, "大暑", "taisho", "Nagy hőség"),
        Sekki(8, 8, "立秋", "risshū", "Ősz kezdete"),
        Sekki(8, 23, "処暑", "shosho", "A hőség vége"),
        Sekki(9, 8, "白露", "hakuro", "Fehér harmat"),
        Sekki(9, 23, "秋分", "shūbun", "Őszi napéjegyenlőség"),
        Sekki(10, 8, "寒露", "kanro", "Hideg harmat"),
        Sekki(10, 23, "霜降", "sōkō", "Dér hullik"),
        Sekki(11, 7, "立冬", "rittō", "Tél kezdete"),
        Sekki(11, 22, "小雪", "shōsetsu", "Kis hó"),
        Sekki(12, 7, "大雪", "taisetsu", "Nagy hó"),
        Sekki(12, 22, "冬至", "tōji", "Téli napforduló"),
    )

    /** A mostani évszak és a következő kezdete. */
    fun now(): Pair<Sekki, Sekki> {
        val c = Calendar.getInstance()
        val key = (c.get(Calendar.MONTH) + 1) * 100 + c.get(Calendar.DAY_OF_MONTH)
        var idx = ALL.indexOfLast { it.month * 100 + it.day <= key }
        if (idx < 0) idx = ALL.size - 1 // január 6. előtt: még Téli napforduló
        return ALL[idx] to ALL[(idx + 1) % ALL.size]
    }
}
