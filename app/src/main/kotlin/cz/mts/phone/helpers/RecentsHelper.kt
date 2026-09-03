package cz.mts.phone.helpers

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.provider.CallLog.Calls
import android.provider.CallLog.Calls.PRESENTATION_UNAVAILABLE
import android.provider.CallLog.Calls.PRESENTATION_UNKNOWN
import cz.mts.base.models.contacts.Contact
import cz.mts.phone.R
import cz.mts.phone.activities.SimpleActivity
import cz.mts.phone.activities.mtsGlobalAll
import cz.mts.phone.extensions.getAvailableSIMCardLabels
import cz.mts.phone.models.RecentCall
import cz.mts.phone.models.SIMAccount
import cz.mts.base.extensions.getIntValue
import cz.mts.base.extensions.getIntValueOrNull
import cz.mts.base.extensions.getLongValue
import cz.mts.base.extensions.getPhoneNumberTypeText
import cz.mts.base.extensions.getStringValue
import cz.mts.base.extensions.getStringValueOrNull
import cz.mts.base.extensions.hasPermission
import cz.mts.base.extensions.toast
import cz.mts.base.helpers.Clipboard.copyTextToClipboard
import cz.mts.base.helpers.PERMISSION_READ_CALL_LOG
import cz.mts.base.helpers.PERMISSION_WRITE_CALL_LOG
import cz.mts.base.helpers.PhoneNumberHelper.areSamePhoneNumber
import cz.mts.base.helpers.PhoneNumberHelper.normalizeDigitsOnly
import cz.mts.base.helpers.ensureBackgroundThread
import cz.mts.base.helpers.getQuestionMarks


class RecentsHelper(private val context: Context) {

    data class CallKey(
        val number: String,
        val type: Int,
        val timeBucket: Long,
        val simId: Int
    )

    sealed class RestoreRecentsResult {
        data class Success(val imported: Int, val failed: Int) : RestoreRecentsResult()
        data class Error(val message: String) : RestoreRecentsResult()
    }

    private val contentUri = Calls.CONTENT_URI

    private companion object {
        const val TIME_BUCKET_MS = 5_000L
    }


    // Observer refresh – pouze nové záznamy (DATE > anchor)
    fun loadRecentsSince(
        anchorTs: Long,
        callback: (List<RecentCall>) -> Unit
    ) {
        if (!context.hasPermission(PERMISSION_READ_CALL_LOG)) {
            callback(emptyList())
            return
        }

        RecentsQueryLimits.setPreviewState(false)
        loadContactsIfNeeded { _ ->
            ensureBackgroundThread {
                val selection = "${Calls.DATE} > ?"
                val args = arrayOf(anchorTs.toString())

                val calls = getRecents(
                    incremental = true,
                    selection = selection,
                    selectionArgs = args
                )
                //   .sortedByDescending { it.startTS }
                //   .distinctBy { it.id }

                callback(calls)
            }
        }
    }


    fun groupSubsequentCalls(calls: List<RecentCall>): List<RecentCall> {
        if (calls.isEmpty()) return emptyList()

        val result = ArrayList<RecentCall>(calls.size)

        var current = calls[0]
        var currentGroup: MutableList<RecentCall>? = null

        for (i in 1 until calls.size) {
            val next = calls[i]

            if (shouldGroupCalls(current, next)) {
                if (currentGroup == null) {
                    // první shoda → založíme group
                    currentGroup = ArrayList()
                    currentGroup.add(current)
                }
                currentGroup.add(next)
            } else {
                // uzavřeme předchozí skupinu
                if (currentGroup != null) {
                    result.add(
                        current.copy(groupedCalls = currentGroup)
                    )
                    currentGroup = null
                } else {
                    result.add(current)
                }
                current = next
            }
        }

        // poslední prvek / skupina
        if (currentGroup != null) {
            result.add(
                current.copy(groupedCalls = currentGroup)
            )
        } else {
            result.add(current)
        }

        return result
    }


    fun shouldGroupCalls(a: RecentCall, b: RecentCall): Boolean {
        //if (a.simID != b.simID) return false
        if (a.dayCode != b.dayCode) return false

        val bothNamedAndDifferent =
            a.name != a.phoneNumber &&
                b.name != b.phoneNumber &&
                a.name != b.name

        if (bothNamedAndDifferent) return false

        return areSamePhoneNumber(a.phoneNumber, b.phoneNumber)
    }


    @SuppressLint("MissingPermission")
    fun loadAllRecentsForExport(): List<RecentCall> {
        if (!context.hasPermission(PERMISSION_READ_CALL_LOG)) {
            return emptyList()
        }

        val result = mutableListOf<RecentCall>()

        val projection = arrayOf(
            Calls._ID,
            Calls.NUMBER,
            Calls.CACHED_NAME,
            Calls.DATE,
            Calls.DURATION,
            Calls.TYPE,
            Calls.PHONE_ACCOUNT_ID,
            Calls.NUMBER_PRESENTATION
        )

        val cursor = context.contentResolver.query(
            Calls.CONTENT_URI,
            projection,
            null,
            null,
            "${Calls.DATE} DESC"
        )

        cursor?.use {
            while (it.moveToNext()) {

                val id = it.getIntValue(Calls._ID)
                val number = it.getStringValueOrNull(Calls.NUMBER).orEmpty()
                val name = it.getStringValueOrNull(Calls.CACHED_NAME).orEmpty()
                val startTs = it.getLongValue(Calls.DATE)
                val duration = it.getIntValue(Calls.DURATION)
                val type = it.getIntValue(Calls.TYPE)
                //    val subscriptionId = it.getStringValueOrNull(Calls.PHONE_ACCOUNT_ID)

                result += RecentCall(
                    id = id,
                    phoneNumber = number,
                    name = name,
                    photoUri = "",
                    startTS = startTs,
                    duration = duration,
                    type = type,
                    simID = -1,  //vše se exportuje s -1, import by měl doplnit své id...
                    simColor = -1,
                    specificNumber = "",
                    specificType = "",
                    isUnknownNumber = false
                )
            }
        }

        return result
    }

    @SuppressLint("NewApi")
    private fun getRecents(
        incremental : Boolean,
        //   contacts: List<Contact>,
        selection: String?,
        selectionArgs: Array<String>?,
        onPreviewReady: ((List<RecentCall>) -> Unit)? = null
    ): List<RecentCall> {

        var processed = 0
        var previewSent = false
        val iLimit = RecentsQueryLimits.getFastLimit()
        val result = mutableListOf<RecentCall>()

        val seenKeys = HashSet<CallKey>(1024)
//        val seenIds = HashSet<Int>(1024) //tím rušíme žrouta paměti distinctby

        val projection = arrayOf(
            Calls._ID,
            Calls.NUMBER,
            Calls.CACHED_NAME,
            Calls.CACHED_PHOTO_URI,
            Calls.DATE,
            Calls.DURATION,
            Calls.TYPE,
            Calls.PHONE_ACCOUNT_ID,
            Calls.NUMBER_PRESENTATION
        )

        val simMap: Map<String, SIMAccount> =
            context.getAvailableSIMCardLabels()
                .sortedBy { it.indexid}
                .take(2)
                .associateBy { it.handle.id }

        val pkg = context.packageName
        val blockAvatar = "android.resource://$pkg/${R.drawable.blockavatar}"
        val anonAvatar  = "android.resource://$pkg/${R.drawable.anonymousavatar}"
        val debugAvatar = "android.resource://$pkg/${R.drawable.karlavatar}"

        val cursor = context.contentResolver.query(
            contentUri,
            projection,
            selection,
            selectionArgs,
            "${Calls.DATE} DESC"
        )

        cursor?.use {
            if (!it.moveToFirst()) return@use

            do {
                // val startTs = it.getLongValue(Calls.DATE)
                val id = it.getIntValue(Calls._ID)

                val startTs = it.getLongValue(Calls.DATE)
                val number = it.getStringValueOrNull(Calls.NUMBER).orEmpty()
                val type = it.getIntValue(Calls.TYPE)
                val subscriptionId = it.getStringValue(Calls.PHONE_ACCOUNT_ID)
                val sim = simMap[subscriptionId]

                val presentation = it.getIntValueOrNull(Calls.NUMBER_PRESENTATION)
                    ?: Calls.PRESENTATION_ALLOWED

                val isUnknown =
                    presentation == PRESENTATION_UNKNOWN ||
                        presentation == PRESENTATION_UNAVAILABLE ||
                        presentation == Calls.PRESENTATION_RESTRICTED ||
                        number.isBlank() ||  // FIX: isNullOrBlank() → isBlank() — number je vždy non-nullable (orEmpty())
                        number == "-1"

                val keyNumber =
                    if (isUnknown) "__unknown__"
                    else normalizeDigitsOnly(number)

                val bucket = startTs / TIME_BUCKET_MS

                val key = CallKey(
                    number = keyNumber,
                    type = type,
                    timeBucket = bucket,
                    simId = sim?.indexid ?: -1
                )


                if (!seenKeys.add(key)) continue

                val contact = if (number.isBlank()) null
                else if (isUnknown) null
                else CacheContacts.findContactByPhoneNumber(number)

                val name: String = if (isUnknown) context.getString(R.string.unknown)
                else contact?.getNameToDisplay() ?: number

                val photoUri = when {
                    mtsGlobalAll.iSaveDebugMode == 2 -> "$debugAvatar?t=$startTs"
                    type == 6 -> "$blockAvatar?t=$startTs"
                    isUnknown -> "$anonAvatar?t=$startTs"
                    else -> contact?.photoUri.orEmpty()
                }

                val duration = it.getIntValue(Calls.DURATION)

                var specificNumber = ""
                var specificType = ""

                val count = contact?.phoneNumbers?.size ?: 0
                if (count > 1) {
                    val phone = contact?.phoneNumbers?.firstOrNull { areSamePhoneNumber(it.value , number) }
                    if (phone != null) {
                        specificNumber = phone.value
                        specificType = context.getPhoneNumberTypeText(phone.type, phone.label)
                    }
                }

                result += RecentCall(
                    id = id,
                    phoneNumber = number.orEmpty(),
                    name = name,
                    photoUri = photoUri,
                    startTS = startTs,
                    duration = duration,
                    type = type,
                    simID = sim?.indexid ?: -1,
                    simColor = sim?.color ?: -1,
                    specificNumber = specificNumber,
                    specificType = specificType,
                    isUnknownNumber = isUnknown
                )

                processed++
                if (processed == iLimit && !incremental && !previewSent) {
                    previewSent = true
                    onPreviewReady?.invoke(result.toList())
                    Thread.yield()
                }

            } while (it.moveToNext())
        }

        if (!incremental) RecentsQueryLimits.recentcount = processed
        else RecentsQueryLimits.recentcount += processed

        return result
    }


    private fun loadContactsIfNeeded(callback: (List<Contact>) -> Unit) {
        CacheContacts.getCachedContacts(context) { contacts -> callback(contacts) }
    }

    fun removeRecentCalls(ids: List<Int>, callback: () -> Unit) {
        if (!context.hasPermission(PERMISSION_WRITE_CALL_LOG)) {
            callback()
            return
        }
        ensureBackgroundThread {
            ids.chunked(30).forEach { chunk ->
                val sel = "${Calls._ID} IN (${getQuestionMarks(chunk.size)})"
                val args = chunk.map { it.toString() }.toTypedArray()
                context.contentResolver.delete(contentUri, sel, args)
            }
            callback()
        }
    }

    @SuppressLint("MissingPermission")
    fun removeAllRecentCalls(activity: SimpleActivity, callback: () -> Unit) {
        activity.handlePermission(PERMISSION_WRITE_CALL_LOG) {
            if (it) {
                ensureBackgroundThread {
                    context.contentResolver.delete(contentUri, null, null)
                    callback()
                }
            }
        }
    }



    fun restoreRecentCalls(
        activity: SimpleActivity,
        calls: List<RecentCall>,
        showImportingToast: Boolean = true,
        callback: (RestoreRecentsResult) -> Unit
    ) {
        activity.handlePermission(PERMISSION_WRITE_CALL_LOG) { granted ->
            if (!granted) {
                callback(RestoreRecentsResult.Error("WRITE_CALL_LOG permission denied"))
                return@handlePermission
            }

            ensureBackgroundThread {
                var imported = 0
                var failed = 0
                val total = calls.size
                val errorInfo = StringBuilder()

                calls.sortedBy { it.startTS }.forEachIndexed { index, call ->
                    try {
                        val values = ContentValues().apply {
                            put(Calls.NUMBER, call.phoneNumber)
                            put(Calls.TYPE, call.type)
                            put(Calls.DATE, call.startTS)
                            put(Calls.DURATION, call.duration)
                            put(Calls.CACHED_NAME, call.name)
                        }

                        val insertedUri = context.contentResolver.insert(contentUri, values)

                        if (insertedUri != null) {
                            imported++
                        } else {
                            failed++
                            errorInfo.append(
                                "\n${index + 1}/$total error: insert() returned null" +
                                    " | number=${call.phoneNumber}" +
                                    " | date=${call.startTS}" +
                                    " | type=${call.type}"
                            )
                        }
                    } catch (e: Exception) {
                        failed++
                        errorInfo.append(
                            "\n${index + 1}/$total error: ${e.message}" +
                                " | number=${call.phoneNumber}" +
                                " | date=${call.startTS}" +
                                " | type=${call.type}"
                        )
                    }
                }

                val summary = "Imported: $imported, Failed: $failed (total $total)"

                if (failed > 0) {
                    copyTextToClipboard(
                        activity,
                        "Import",
                        summary + errorInfo.toString()
                    )
                }

                if (showImportingToast) {
                    activity.toast(summary)
                }

                callback(RestoreRecentsResult.Success(imported, failed))
            }
        }
    }

    private fun flatten(call: RecentCall): List<RecentCall> =
        call.groupedCalls ?: listOf(call)


    fun mergeGroupedCalls(
        newest: RecentCall,
        existing: RecentCall
    ): RecentCall {

        val uniqueCalls =
            (flatten(newest) + flatten(existing))
                .distinctBy { it.id }
                .sortedByDescending { it.startTS }

        val header = uniqueCalls.first()

        val grouped = buildList {
            add(header)                 // povinná duplicita
            addAll(uniqueCalls.drop(1)) // ostatní hovory
        }

        return header.copy(
            groupedCalls = grouped.toMutableList()
        )
    }


    fun loadAllRecentsChunked(
        chunkSize: Int,
        onChunk: (List<RecentCall>, isLast: Boolean) -> Unit
    ) {
        if (!context.hasPermission(PERMISSION_READ_CALL_LOG)) {
            onChunk(emptyList(), true)
            return
        }

        val simMap = context.getAvailableSIMCardLabels()
            .sortedBy { it.indexid }.take(2)
            .associateBy { it.handle.id }

        loadContactsIfNeeded { _ ->
            ensureBackgroundThread {
                getRecentsChunked(chunkSize, simMap, onChunk)
            }
        }
    }

    /**
     * Otevře jediný cursor přes celý call-log (bez limit/offset parametrů)
     * a sám ho dávkuje po [chunkSize] raw řádcích.
     *
     * Proč jeden cursor:
     *  - Vyhýbáme se vendor-specifickému chování limit/offset (někteří ignorují,
     *    jiní si nastaví vlastní hodnotu → nekonečná smyčka nebo zkrácení).
     *  - Detekce konce je triviální: cursor.moveToNext() vrátí false.
     *  - Žádná race condition — vše běží sekvenčně na jednom vlákně.
     *
     * Detekce konce chunků:
     *  - isLast = rawRowsInChunk < chunkSize
     *    (počítáme RAW řádky, ne výsledek po filtraci seenKeys)
     */
    @SuppressLint("NewApi")
    private fun getRecentsChunked(
        chunkSize: Int,
        simMap: Map<String, SIMAccount>,
        onChunk: (List<RecentCall>, isLast: Boolean) -> Unit
    ) {
        val projection = arrayOf(
            Calls._ID,
            Calls.NUMBER,
            Calls.CACHED_NAME,
            Calls.CACHED_PHOTO_URI,
            Calls.DATE,
            Calls.DURATION,
            Calls.TYPE,
            Calls.PHONE_ACCOUNT_ID,
            Calls.NUMBER_PRESENTATION
        )

        val pkg = context.packageName
        val blockAvatar = "android.resource://$pkg/${R.drawable.blockavatar}"
        val anonAvatar  = "android.resource://$pkg/${R.drawable.anonymousavatar}"
        val debugAvatar = "android.resource://$pkg/${R.drawable.karlavatar}"

        val seenKeys = HashSet<CallKey>(1024)

        context.contentResolver.query(
            contentUri, projection, null, null, "${Calls.DATE} DESC"
        )?.use { cursor ->

            var chunkRaw = 0          // počet raw řádků v aktuálním chunku
            var chunkResult = mutableListOf<RecentCall>()

            fun flush(isLast: Boolean) {
                onChunk(chunkResult, isLast)
                chunkResult = mutableListOf()
                chunkRaw = 0
            }

            while (cursor.moveToNext()) {
                chunkRaw++

                // ── čtení sloupců ──────────────────────────────────────────
                val id        = cursor.getIntValue(Calls._ID)
                val startTs   = cursor.getLongValue(Calls.DATE)
                val number    = cursor.getStringValueOrNull(Calls.NUMBER).orEmpty()
                val type      = cursor.getIntValue(Calls.TYPE)
                val subscriptionId = cursor.getStringValue(Calls.PHONE_ACCOUNT_ID)
                val sim       = simMap[subscriptionId]

                val presentation = cursor.getIntValueOrNull(Calls.NUMBER_PRESENTATION)
                    ?: Calls.PRESENTATION_ALLOWED

                val isUnknown = presentation == PRESENTATION_UNKNOWN ||
                    presentation == PRESENTATION_UNAVAILABLE ||
                    presentation == Calls.PRESENTATION_RESTRICTED ||
                    number.isBlank() || number == "-1"

                val keyNumber = if (isUnknown) "__unknown__" else normalizeDigitsOnly(number)
                val key = CallKey(keyNumber, type, startTs / TIME_BUCKET_MS, sim?.indexid ?: -1)

                // ── deduplikace (přeskočíme filtrovaný řádek, ale RAW čítač
                //    jsme už zvýšili — chunk se musí uzavřít podle raw počtu) ──
                if (seenKeys.add(key)) {
                    val contact = if (isUnknown || number.isBlank()) null
                    else CacheContacts.findContactByPhoneNumber(number)

                    val name = if (isUnknown) context.getString(R.string.unknown)
                    else contact?.getNameToDisplay() ?: number

                    val photoUri = when {
                        mtsGlobalAll.iSaveDebugMode == 2 -> "$debugAvatar?t=$startTs"
                        type == 6   -> "$blockAvatar?t=$startTs"
                        isUnknown   -> "$anonAvatar?t=$startTs"
                        else        -> contact?.photoUri.orEmpty()
                    }

                    var specificNumber = ""
                    var specificType = ""
                    if ((contact?.phoneNumbers?.size ?: 0) > 1) {
                        contact?.phoneNumbers?.firstOrNull { areSamePhoneNumber(it.value, number) }
                            ?.also {
                                specificNumber = it.value
                                specificType = context.getPhoneNumberTypeText(it.type, it.label)
                            }
                    }

                    chunkResult += RecentCall(
                        id = id, phoneNumber = number, name = name, photoUri = photoUri,
                        startTS = startTs, duration = cursor.getIntValue(Calls.DURATION),
                        type = type, simID = sim?.indexid ?: -1, simColor = sim?.color ?: -1,
                        specificNumber = specificNumber, specificType = specificType,
                        isUnknownNumber = isUnknown
                    )
                }

                // ── odeslání chunku po naplnění raw limitu ─────────────────
                if (chunkRaw == chunkSize) {
                    val hasMore = !cursor.isLast
                    flush(isLast = !hasMore)
                    if (!hasMore) return@use
                }
            }

            // ── zbývající řádky (poslední, neúplný chunk) ──────────────────
            flush(isLast = true)
        } ?: onChunk(emptyList(), true)  // cursor == null (no permission / provider error)
    }

}
