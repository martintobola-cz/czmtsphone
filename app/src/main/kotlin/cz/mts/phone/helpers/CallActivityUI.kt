package cz.mts.phone.helpers

object CallActivityUI {

     var sUUIDonNotification : String = ""
     var bUUIDonNotificationConference : Boolean = false
     var sUUIDonUI : String = ""
     var bUUIDonUIConference : Boolean = false

     private var state = 0
    // 0 = destroyed
    // 1 = visible
    // 2 = launching
    // 3 = onpause


    fun isVisible() = state == 1
    fun isLaunching() = state == 2
    fun isDestroyed() = state == 0
    fun isPaused() = state == 3

    fun markPaused() { state = 3}
    //fun markLaunching() { state = 2 }
    fun markVisible() { state = 1 }
    fun markDestroyed() {
        sUUIDonUI = ""
        bUUIDonUIConference = false
        state = 0
    }


}
