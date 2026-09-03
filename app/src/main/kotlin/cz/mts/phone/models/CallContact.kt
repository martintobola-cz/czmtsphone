package cz.mts.phone.models

// a simpler Contact model containing just info needed at the call screen
data class CallContact(
    var id: Long,
    var name: String,
    var photoUri: String,
    var number: String,
    var numberLabel: String,
    var presentation : Int,
    var source : String,
    )

