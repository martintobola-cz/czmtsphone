package cz.mts.base.models.contacts

import cz.mts.base.helpers.MTS_PHONE

data class ContactSource(var name: String, var type: String, var publicName: String, var count: Int = 0) {
    fun getFullIdentifier(): String {
        return if (type == MTS_PHONE) {
            type
        } else {
            "$name:$type"
        }
    }
}
