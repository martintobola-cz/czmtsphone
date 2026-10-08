package cz.mts.base.helpers

import android.net.Uri
import cz.mts.base.activities.BaseSimpleActivity
import cz.mts.base.extensions.hasPermission
import cz.mts.base.extensions.toast
import cz.mts.base.helpers.Clipboard.copyTextToClipboard
import cz.mts.base.models.PhoneNumber
import cz.mts.base.models.contacts.Contact
import android.provider.ContactsContract.CommonDataKinds.Phone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * "Falešný import": místo VCF souboru se čtou kontakty přímo ze SIM (ICC/ADN)
 * a ukládají se natvrdo do skrytého úložiště (MTS_PHONE).
 * Používá stejné Progress / Result jako VcfImporter, takže volající kód je stejný.
 */
object SimImporter {

    suspend fun import(
        activity: BaseSimpleActivity,
        subId: Int,
        showToast: Boolean,
        onProgress: (VcfImporter.Progress) -> Unit,
    ): VcfImporter.Result = withContext(Dispatchers.IO) {

        if (subId < 0 || !activity.hasPermission(PERMISSION_READ_CONTACTS)) {
            return@withContext fail(activity, "No READ_CONTACTS permission or invalid subId ($subId)", showToast)
        }

        // --- 1) přečíst SIM ---
        val entries = ArrayList<Pair<String, String>>() // name, number
        try {
            val uri = Uri.parse("content://icc/adn/subId/$subId")
            val cursor = activity.contentResolver.query(uri, null, null, null, null)
                ?: return@withContext fail(activity, "SIM provider returned no data (subId $subId)", showToast)

            cursor.use {
                val nameIdx = it.getColumnIndex("name")
                val numberIdx = it.getColumnIndex("number")
                if (numberIdx < 0) throw IllegalStateException("column 'number' missing")

                while (it.moveToNext()) {
                    val name = if (nameIdx >= 0) it.getString(nameIdx)?.trim().orEmpty() else ""
                    val number = it.getString(numberIdx)?.trim().orEmpty()
                    if (name.isNotEmpty() || number.isNotEmpty()) entries.add(name to number)
                }
            }
        } catch (e: Exception) {
            return@withContext fail(activity, "SIM read error: ${e.message}", showToast)
        }

        // --- 2) klíče kontaktů, které už ve skrytém úložišti jsou (jméno|číslo) ---
        val helper = LocalContactsHelper(activity)
        val existingKeys = HashSet<String>()
        helper.getAllContacts().forEach { c ->
            val name = c.getNameToDisplay().lowercase()
            if (c.phoneNumbers.isEmpty()) {
                existingKeys.add("$name|")
            } else {
                c.phoneNumbers.forEach { existingKeys.add("$name|${PhoneNumberHelper.normalizeDigitsOnly(it.value)}") }
            }
        }

        // --- 3) vložit ---
        var imported = 0
        var failed = 0
        var duplicates = 0
        val total = entries.size
        var sInfo = ""

        entries.forEachIndexed { index, (name, number) ->
            try {
                val normalized = PhoneNumberHelper.normalizeDigitsOnly(number)
                val contact = Contact(id = 0, contactId = 0).apply {
                    firstName = name
                    if (number.isNotEmpty()) {
                        phoneNumbers = arrayListOf(
                            PhoneNumber(
                                value = number,
                                type = Phone.TYPE_MOBILE,
                                label = "",
                                normalizedNumber = normalized,
                                isPrimary = false
                            )
                        )
                    }
                }
                val key = "${contact.getNameToDisplay().lowercase()}|${if (number.isEmpty()) "" else normalized}"
                when {
                    !existingKeys.add(key) -> duplicates++
                    helper.insertOrUpdateContact(contact) -> imported++
                    else -> throw IllegalStateException("insertOrUpdateContact returned false")
                }
            } catch (e: Exception) {
                sInfo += "\r\n${index}/${total} error:\r\n${name} ${number}: ${e.message}"
                failed++
            }
            onProgress(VcfImporter.Progress(index + 1, total))
        }

        val sInfo2 = "Imported: $imported (total $total, duplicates skipped $duplicates)"
        if (failed > 0) {
            copyTextToClipboard(activity, "Import", sInfo2 + sInfo)
        }
        if (showToast) {
            activity.toast(sInfo2)
        }

        VcfImporter.Result.Success(imported, failed)
    }

    // stejný vzor jako VcfImporter: detail chyby do schránky, výsledek Error(IMPORT_FAIL)
    private fun fail(activity: BaseSimpleActivity, message: String, showToast: Boolean): VcfImporter.Result {
        copyTextToClipboard(activity, "Import", message)
        if (showToast) activity.toast("SIM import failed: $message")
        return VcfImporter.Result.Error(ExportResult.IMPORT_FAIL)
    }
}