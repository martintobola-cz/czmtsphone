package cz.mts.base.helpers


//vrátí ISO 3166-1 alpha-2 země SIM karty, fallback CZ

object MySIMcountryISO {
    private var sISO = Array(2) { "" }
    private var isimID = IntArray(2) { -2 }


    fun getISO(simID: Int): String =
        when (simID) {
            isimID[0] -> sISO[0]
            isimID[1] -> sISO[1]
            else -> ""
        }


    fun setISO(index: Int, data: String, simID: Int) {
        if (data.isBlank() || index !in 0..1) return
        sISO[index] = data
        isimID[index] = simID
    }


}
