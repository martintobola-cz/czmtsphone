package cz.mts.phone.fragments

import android.content.Context
import android.graphics.drawable.InsetDrawable
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import cz.mts.base.extensions.beGone
import cz.mts.base.extensions.beVisible
import cz.mts.base.extensions.density
import cz.mts.base.extensions.getProperPrimaryColor
import cz.mts.base.extensions.hasPermission
import cz.mts.base.extensions.normalizeString
import cz.mts.base.helpers.PERMISSION_READ_CALL_LOG
import cz.mts.base.helpers.ensureBackgroundThread
import cz.mts.phone.R
import cz.mts.phone.activities.SimpleActivity
import cz.mts.phone.activities.mtsGlobalAll
import cz.mts.phone.adapters.RecentCallsAdapter
import cz.mts.phone.databinding.FragmentRecentsBinding
import cz.mts.base.extensions.baseConfig as config
import cz.mts.phone.extensions.startContactDetailsIntentY
import cz.mts.phone.helpers.RecentsHelper
import cz.mts.phone.helpers.RecentsQueryLimits
import cz.mts.phone.models.CallLogItem
import cz.mts.phone.models.RecentCall
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class RecentsFragment(
    context: Context,
    attrs: AttributeSet
) : MyViewPagerFragment<MyViewPagerFragment.RecentsInnerBinding>(context, attrs) {

    // -----------------------------
    // STATE
    // -----------------------------
    private enum class State { INIT, LOADING, READY }
    private var state = State.INIT
    private var searchQuery: String? = null




    // -----------------------------
    // DATA
    // -----------------------------
    private var anchorTs: Long = 0L
    private var allItems: List<CallLogItem> = emptyList()

    // callback volaný po každém přijatém (ne-posledním) chunku – pro UI feedback typu "stále běží"
    var onChunkProgress: (() -> Unit)? = null

    // -----------------------------
    // HISTORIE HOVORŮ PRO KONKRÉTNÍ KONTAKT (ContactCallHistoryDialog)
    // -----------------------------
    // Menší, negroupovaný seznam - jen hovory, u kterých byl rozpoznán konkrétní kontakt
    // (tj. name != phoneNumber/specificNumber a nejde o neznámé/blokované číslo).
    // Odvozuje se z allItems při každé jeho změně, takže se plní stejně postupně (chunky).
    private var knownContactCalls: List<RecentCall> = emptyList()

    // callback volaný pokaždé, když se knownContactCalls změní (i po částech, dokud recents dobíhá)
    var onKnownContactCallsChanged: (() -> Unit)? = null

    /** Vrátí aktuální (negroupovaný) seznam hovorů pro daný zobrazovaný název kontaktu. */
    fun getCallsForContact(contactName: String): List<RecentCall> =
        knownContactCalls.filter { it.name == contactName }

    private fun updateKnownContactCalls() {
        knownContactCalls = allItems
            .filterIsInstance<RecentCall>()
            .flatMap { it.groupedCalls ?: listOf(it) } // pro tento pohled vždy negroupovaně
            .filter { !it.isUnknownNumber && it.name != it.phoneNumber && it.name != it.specificNumber }
            .distinctBy { it.id }
            .sortedByDescending { it.startTS }
        onKnownContactCallsChanged?.invoke()
    }

    // -----------------------------
    // UI
    // -----------------------------
    private lateinit var binding: FragmentRecentsBinding
    private var adapter: RecentCallsAdapter? = null

    // -----------------------------
    // HELPERS
    // -----------------------------
    private val recentsHelper = RecentsHelper(context)
    private val mainHandler = Handler(Looper.getMainLooper())

    // -----------------------------
    // SCROLL INDICATOR
    // -----------------------------
    private var scrollHideJob: Job? = null



    private fun applyGrouping(calls: List<RecentCall>): List<RecentCall> {
        return if (context.config.groupSubsequentCalls) {
            recentsHelper.groupSubsequentCalls(calls)
        } else {
            calls
        }
    }

    fun refreshItems(forceX: Boolean = false, callback: (() -> Unit)?) {
        if (!context.hasPermission(PERMISSION_READ_CALL_LOG))
        {
            showPlaceholderPerms()
            hideProgress()
            callback?.invoke()
            return
        }

        val force = if (RecentsQueryLimits.getRefreshState()) true
        else forceX
        RecentsQueryLimits.setRefreshState(false)
        when (state) {
            State.INIT -> {
                // fragment ještě nebyl inicializován → nic nedělej
                callback?.invoke() // můžeš zavolat hned, pokud chceš signalizovat "hotovo"
            }

            State.LOADING -> {
                // už se načítá → ignoruj (žádné pending flagy)
                // callback nemusíš volat, nebo můžeš uložit do fronty pro pozdější zavolání
            }

            State.READY -> {
                if (force) {
                    // tvrdý refresh – celé znovunačtení
                    anchorTs = 0L
                    allItems = emptyList()
                    loadInitialData {
                        // zavolání callbacku po dokončení loadInitialData
                        callback?.invoke()
                    }
                } else {
                    // jemný refresh – jen observer logika
                    refreshFromObserver {callback?.invoke()}
                }
            }
        }
    }



    override fun setupColors(
        textColor: Int,
        primaryColor: Int,
        properPrimaryColor: Int
    ) {
        binding.recentsPlaceholder.setTextColor(textColor)
        binding.progressIndicator.setIndicatorColor(properPrimaryColor)

        adapter?.apply {
            updateTextColor(textColor)
            initDrawables()
        }
    }

    override fun onSearchClosed() {
        searchQuery = ""
        applySearch()
    }

    override fun onSearchQueryChanged(text: String) {
        searchQuery = text
        applySearch()
    }


    private fun applySearch() {
        val query = searchQuery?.trim()?.replace("\\s+".toRegex(), " ").orEmpty()
        //  val query = searchQuery?.trim().orEmpty()

        if (query.isEmpty()) {
            adapter?.updateItems(allItems, "", true)
            showPlaceholder(allItems.isEmpty())
            return
        }

        ensureBackgroundThread {
            val normalizedQuery = query.normalizeString()
            val filteredCalls = allItems
                .filterIsInstance<RecentCall>()
                .filter {
                    (it.name.normalizeString()).contains(normalizedQuery, ignoreCase = true) ||
                        it.doesContainPhoneNumber(normalizedQuery)
                }
                .sortedByDescending { it.startTS }

            val filteredItems = groupCallsByDate(filteredCalls)

            mainHandler.post {
                adapter?.updateItems(filteredItems, query)
                showPlaceholder(filteredItems.isEmpty())
            }
        }

    }

    // -----------------------------
    // LIFECYCLE
    // -----------------------------
    override fun onFinishInflate() {
        super.onFinishInflate()
        binding = FragmentRecentsBinding.bind(this)
        innerBinding = RecentsInnerBinding(binding)
    }

    //oncreate
    override fun setupFragment() {
        if (!context.hasPermission(PERMISSION_READ_CALL_LOG)) {
            showPlaceholderPerms()
            return
        }

        setupRecycler()
        setupScrollIndicator()
        loadInitialData()
    }


    // -----------------------------
    // INITIAL LOAD
    // -----------------------------


    private fun loadInitialData(onDone: (() -> Unit)? = null) {
        state = State.LOADING
        //hideProgress()
        if (context.hasPermission(PERMISSION_READ_CALL_LOG)) showProgress()
        else hideProgress()

        // Tyto promenne vlastni vyhradne worker thread, na kterem
        // RecentsHelper.loadAllRecentsChunked() sekvencne vola callbacky.
        val accumulated = mutableListOf<CallLogItem>()
        var maxTs = 0L

        // Posledni dayCode, ktery uz je v accumulated.
        // Aktualizuje se na stejnem worker threadu jeste pred zpracovanim dalsiho chunku.
        var lastAccumulatedDayCode = ""

        val chunklimit = RecentsQueryLimits.getChunkLimit()
        recentsHelper.loadAllRecentsChunked(chunkSize = chunklimit) { chunk, isLast ->

            // POZOR: uz jsme na sekvencnim worker threadu z RecentsHelper.
            // Nevytvarime dalsi ensureBackgroundThread, aby se chunky nemohly zpracovavat paralelne.

            // Nejprve seskupime pouze aktualni chunk. Predchozi raw polozku sem zamerne
            // nepridavame - pokud uz byla soucasti skupiny v predchozim chunku, vznikla by
            // v dalsim kroku vnorená/neuplna skupina a mohly by se ztratit starsi hovory.
            val groupedCurrentChunk = if (context.config.groupSubsequentCalls)
                recentsHelper.groupSubsequentCalls(chunk)
            else chunk

            // Pokud prvni skupina aktualniho chunku navazuje na posledni skupinu
            // v accumulated, sloucime obe KOMPLETNI skupiny a prvni polozku chunku
            // pak uz znovu nepridavame.
            val firstCurrentIndex = mergeFirstGroupAcrossChunkBoundary(
                accumulated = accumulated,
                groupedCurrentChunk = groupedCurrentChunk
            )

            // Pokud se prvni skupina aktualniho chunku sloucila s posledni skupinou
            // v accumulated, uz ji znovu nepridavame. Zbytek chunku zpracujeme normalne.
            val groupedToShow = if (firstCurrentIndex == 0)
                groupedCurrentChunk
            else
                groupedCurrentChunk.drop(firstCurrentIndex)

            // groupCallsByDate dostane aktualni lastDay z accumulated.
            // Pokud groupedToShow zacina stejnym dnem, dalsi Date header nevlozi.
            val withDates = groupCallsByDate(groupedToShow, lastAccumulatedDayCode)

            // DULEZITE: accumulated i lastAccumulatedDayCode se meni pouze zde,
            // na jednom worker threadu, nikdy uvnitr mainHandler.post.
            accumulated.addAll(withDates)

            lastAccumulatedDayCode = accumulated
                .filterIsInstance<RecentCall>()
                .lastOrNull()?.dayCode ?: lastAccumulatedDayCode

            if (chunk.isNotEmpty()) {
                maxTs = maxOf(maxTs, chunk.maxOf { it.startTS })
            }

            // Main thread dostane pouze immutable snapshot aktualniho stavu.
            val snapshot = accumulated.toList()
            val snapshotMaxTs = maxTs

            mainHandler.post {
                hideProgress()  // i kdyz jeste neni nacteny cely log, progress schovame

                allItems = snapshot
                updateKnownContactCalls()

                if (searchQuery.isNullOrEmpty()) {
                    adapter?.updateItems(snapshot)
                    showPlaceholder(snapshot.isEmpty())
                } else {
                    applySearch()   // aplikuje aktualni filtr i na tenhle dilci chunk
                }

                if (isLast) {
                    anchorTs = snapshotMaxTs
                    state = State.READY
                    onDone?.invoke()
                } else {
                    onChunkProgress?.invoke()   // signal "jeste bezim" jen pro mezikroky
                }
            }
        }
    }

    /**
     * Slouci posledni uz hotovou skupinu z predchoziho chunku s prvni skupinou
     * aktualniho chunku, pokud na sebe hovory navazuji.
     *
     * Vracena hodnota je pocet polozek, ktere se maji preskocit na zacatku
     * groupedCurrentChunk. Je to 1 po uspesnem merge, jinak 0.
     *
     * accumulated je meneno pouze na worker threadu z loadAllRecentsChunked().
     */
    private fun mergeFirstGroupAcrossChunkBoundary(
        accumulated: MutableList<CallLogItem>,
        groupedCurrentChunk: List<RecentCall>
    ): Int {
        if (!context.config.groupSubsequentCalls || groupedCurrentChunk.isEmpty()) return 0

        val previousIndex = accumulated.indexOfLast { it is RecentCall }
        if (previousIndex < 0) return 0

        val previousCall = accumulated[previousIndex] as RecentCall
        val firstCurrent = groupedCurrentChunk.first()

        if (!recentsHelper.shouldGroupCalls(previousCall, firstCurrent)) return 0

        // previousCall muze byt uz cela skupina z predchoziho chunku a firstCurrent
        // muze byt cela skupina z aktualniho chunku. mergeGroupedCalls() obe strany
        // flattenuje, deduplikuje podle id a znovu seradi podle startTS.
        accumulated[previousIndex] = recentsHelper.mergeGroupedCalls(
            newest = previousCall,
            existing = firstCurrent
        )

        return 1
    }



    private fun refreshFromObserver(onDone: (() -> Unit)? = null) {

        var doneCalled = false
        fun finish() {
            if (!doneCalled) {
                doneCalled = true
                onDone?.invoke()
            }
        }

        recentsHelper.loadRecentsSince(anchorTs - 1) { newCalls ->

            val validNewCallsCount = newCalls.count { nc ->
                allItems.none { it is RecentCall && it.id == nc.id }
            }

            //nic nového
            if (validNewCallsCount <= 0) {
                finish()
                return@loadRecentsSince
            }

            ensureBackgroundThread {
                prepareCallLog(newCalls) { preparedItems ->
                    if (preparedItems.isEmpty()) {
                        finish()
                        return@prepareCallLog
                    }

                    mainHandler.post {

                        val firstOldIndex = allItems.indexOfFirst { it is RecentCall }
                        val oldCall = if (firstOldIndex != -1) allItems[firstOldIndex] as RecentCall else null

                        // 1) odříznutí duplicitního tailu bez kopírování listu
                        var preparedEnd = preparedItems.size
                        if (oldCall != null) {
                            while (preparedEnd > 0) {
                                val it = preparedItems[preparedEnd - 1]
                                if (it is RecentCall &&
                                    it.id == oldCall.id &&
                                    it.startTS == oldCall.startTS
                                ) {
                                    preparedEnd--
                                } else break
                            }
                            if (preparedEnd == 0) {
                                finish()
                                return@post
                            }
                        }

                        // 2) odstranění nepoužitých Date headerů (streamově)
                        val preparedFiltered = ArrayList<CallLogItem>(preparedEnd)
                        run {
                            var i = 0
                            while (i < preparedEnd) {
                                val item = preparedItems[i]
                                if (item is CallLogItem.Date) {
                                    val next = preparedItems.getOrNull(i + 1)
                                    if (next is RecentCall && next.dayCode == item.dayCode) {
                                        preparedFiltered.add(item)
                                    }
                                } else {
                                    preparedFiltered.add(item)
                                }
                                i++
                            }
                        }

                        val firstNewIndex = preparedFiltered.indexOfLast { it is RecentCall }
                        if (firstNewIndex < 0) {
                            finish()
                            return@post
                        }

                        val newCall = preparedFiltered[firstNewIndex] as RecentCall

                        // 3) kontrola Date header kolize
                        var bRemoveDate = false
                        val dateIndexNew = preparedFiltered.indexOfLast { it is CallLogItem.Date }
                        val dateIndexOld = allItems.indexOfFirst { it is CallLogItem.Date }

                        if (dateIndexNew != -1 && dateIndexOld != -1) {
                            val dn = preparedFiltered[dateIndexNew] as CallLogItem.Date
                            val dox = allItems[dateIndexOld] as CallLogItem.Date
                            if (dn.dayCode == dox.dayCode) {
                                bRemoveDate = true
                            }
                        }

                        // 4) grouping (beze změny sémantiky)
                        if (
                            context.config.groupSubsequentCalls &&
                            oldCall != null &&
                            recentsHelper.shouldGroupCalls(oldCall, newCall)
                        ) {
                            val mergedCall = recentsHelper.mergeGroupedCalls(newCall, oldCall)

                            val result = ArrayList<CallLogItem>(
                                preparedFiltered.size + allItems.size
                            )

                            // nové položky kromě newCall
                            preparedFiltered.forEach {
                                if (it !== newCall) result.add(it)
                            }

                            // zachování případného Date headeru před oldCall
                            if (firstOldIndex > 0 &&
                                allItems[firstOldIndex - 1] is CallLogItem.Date &&
                                !bRemoveDate
                            ) {
                                result.add(allItems[firstOldIndex - 1])
                            }

                            result.add(mergedCall)
                            result.addAll(allItems.drop(firstOldIndex + 1))

                            allItems = result
                            submitList(allItems)
                            anchorTs = maxOf(anchorTs, newCalls.maxOf { it.startTS })
                            scrollToTop()
                            finish()
                            return@post
                        }

                        // 5) finální merge + deduplikace bez distinctBy
                        val result = ArrayList<CallLogItem>(
                            preparedFiltered.size + allItems.size
                        )

                        val seenCallIds = HashSet<Int>()
                        val seenDays = HashSet<String>()

                        fun addItem(it: CallLogItem) {
                            when (it) {
                                is RecentCall -> if (seenCallIds.add(it.id)) result.add(it)
                                is CallLogItem.Date -> if (seenDays.add(it.dayCode)) result.add(it)
                            }
                        }

                        preparedFiltered.forEach(::addItem)

                        val oldItemsSource =
                            if (bRemoveDate && dateIndexOld != -1)
                                allItems.drop(dateIndexOld + 1)
                            else allItems

                        oldItemsSource.forEach(::addItem)

                        allItems = result
                        submitList(allItems)
                        anchorTs = maxOf(anchorTs, newCalls.maxOf { it.startTS })
                        scrollToTop()
                        finish()
                    }
                }
            }
        }
    }


    private fun removeUnusedDateHeaders(items: List<CallLogItem>): List<CallLogItem> {
        if (items.isEmpty()) return items

        val result = ArrayList<CallLogItem>(items.size)

        var i = 0
        while (i < items.size) {
            val item = items[i]

            if (item is CallLogItem.Date) {
                val next = items.getOrNull(i + 1)
                if (next is RecentCall && next.dayCode == item.dayCode) {
                    result.add(item)   // má smysl, necháme
                }
                // jinak ji zahodíme
            } else {
                result.add(item)
            }
            i++
        }
        return result
    }


    // -----------------------------
    // UI HELPERS
    // -----------------------------
    private fun setupRecycler() {
        if (adapter != null) return

        adapter = RecentCallsAdapter(
            compactMode = false,
            activity = activity as SimpleActivity,
            recyclerView = binding.recentsList,
            showOverflowMenu = true,
            itemClick = {
                activity?.let { act ->
                    mtsGlobalAll.mtsCallRecentCall(act, it as RecentCall, -2, false)
                }
            },
            itemDelete = { deletedItems ->
                val deletedIds = deletedItems
                    .filterIsInstance<RecentCall>()
                    .map { it.getItemId() }
                    .toSet()

                if (deletedIds.isNotEmpty()) {
                    allItems = allItems
                        .filterNot {it is RecentCall && it.getItemId() in deletedIds }
                        .let { removeUnusedDateHeaders(it) }
                    updateKnownContactCalls()
                    applySearch()
                }
            },

            profileIconClick = {
                activity?.startContactDetailsIntentY(it as RecentCall)
            }
        )

        binding.recentsList.adapter = adapter
    }


    private fun submitList(list: List<CallLogItem>) {
        allItems = list
        updateKnownContactCalls()
        adapter?.updateItems(list)
        showPlaceholder(list.isEmpty())
    }

    private fun showProgress() {
        binding.progressIndicator.beVisible()
    }

    private fun hideProgress() {
        binding.progressIndicator.beGone()
    }

    private fun showPlaceholderPerms() {
        binding.recentsPlaceholder.beVisible()
        binding.recentsList.beGone()
        binding.recentsPlaceholder.text = context.getString(R.string.could_not_access_the_call_history)
    }

    private fun showPlaceholder(show: Boolean) {
        if (show) {
            binding.recentsPlaceholder.beVisible()
            binding.recentsList.beGone()
        } else {
            binding.recentsPlaceholder.beGone()
            binding.recentsList.beVisible()
        }
    }

    private fun setupScrollIndicator() {
        val indicator = binding.scrollIndicator
        val recycler = binding.recentsList

        // FIX (touch area): View je v XML 32dp široký, ale vizuál scroll_indicator_bg
        // chceme jen jako 4dp pruh u pravého okraje.
        // InsetDrawable odsadí drawable zleva o 24dp a zprava o 4dp →
        // výsledný barevný pruh = 32 - 24 - 4 = 4dp, 4dp od okraje obrazovky.
        // Touch plocha zůstane 32dp, prst ho trefí spolehlivě.
        val baseDrawable = ContextCompat.getDrawable(context, R.drawable.scroll_indicator_bg)
            ?.mutate()
        if (baseDrawable != null) {
            DrawableCompat.setTint(baseDrawable, context.getProperPrimaryColor())
            val density = context.density()
            indicator.background = InsetDrawable(
                baseDrawable,
                (24 * density).toInt(),  // left inset
                0,
                (4 * density).toInt(),   // right inset – mezera od okraje
                0
            )
        }

        recycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                val offset = rv.computeVerticalScrollOffset().toFloat()
                val extent = rv.computeVerticalScrollExtent().toFloat()
                val range  = rv.computeVerticalScrollRange().toFloat()

                if (range <= extent) {
                    indicator.visibility = View.INVISIBLE
                    return
                }

                val fraction = offset / (range - extent) // 0.0 – 1.0

                // Přímé nastavení bez doOnLayout – height je po prvním layoutu vždy platný
                if (indicator.height > 0) {
                    val maxY = rv.height - indicator.height.toFloat()
                    indicator.translationY = fraction * maxY
                }
                indicator.visibility = View.VISIBLE

                // Schovat po 1 s nečinnosti
                scrollHideJob?.cancel()
                scrollHideJob = recycler.findViewTreeLifecycleOwner()
                    ?.lifecycleScope?.launch {
                        delay(1300)
                        indicator.animate().alpha(0f).withEndAction {
                            indicator.visibility = View.INVISIBLE
                            indicator.alpha = 1f
                        }
                    }
            }
        })

        indicator.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    scrollHideJob?.cancel()
                    indicator.animate().cancel()
                    indicator.alpha = 1f
                    indicator.visibility = View.VISIBLE
                    v.parent?.requestDisallowInterceptTouchEvent(true)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val location = IntArray(2)
                    recycler.getLocationOnScreen(location)
                    val recyclerTopOnScreen = location[1].toFloat()

                    val rawY = event.rawY - recyclerTopOnScreen - (indicator.height / 2f)
                    val fraction = (rawY / (recycler.height - indicator.height))
                        .coerceIn(0f, 1f)

                    val range  = recycler.computeVerticalScrollRange().toFloat()
                    val extent = recycler.computeVerticalScrollExtent().toFloat()
                    val targetOffset = (fraction * (range - extent)).toInt()

                    recycler.scrollBy(0, targetOffset - recycler.computeVerticalScrollOffset())
                    scrollHideJob?.cancel()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.parent?.requestDisallowInterceptTouchEvent(false)
                    if (event.action == MotionEvent.ACTION_UP) v.performClick()

                    // FIX: po puštění prstu spustit timer pro schování indikátoru
                    scrollHideJob?.cancel()
                    scrollHideJob = recycler.findViewTreeLifecycleOwner()
                        ?.lifecycleScope?.launch {
                            delay(1300)
                            indicator.animate().alpha(0f).withEndAction {
                                indicator.visibility = View.INVISIBLE
                                indicator.alpha = 1f
                            }
                        }

                    true
                }
                else -> false
            }
        }
    }

    // -----------------------------
    // PREPARE LOG (zachováno)
    // -----------------------------
    private fun prepareCallLog(
        calls: List<RecentCall>,
        callback: (List<CallLogItem>) -> Unit
    ) {
        if (calls.isEmpty()) {
            callback(emptyList())
            return
        }
        ensureBackgroundThread {
            val groupedCalls = applyGrouping(calls)

            val items = groupCallsByDate(groupedCalls)
            callback(items)
        }
    }

    private fun groupCallsByDate(calls: List<RecentCall>, lastKnownDay: String = ""): List<CallLogItem> {
        val result = mutableListOf<CallLogItem>()
        var lastDay = lastKnownDay

        for (call in calls) {
            if (call.dayCode != lastDay) {
                result += CallLogItem.Date(call.startTS, call.dayCode)
                lastDay = call.dayCode
            }
            result += call
        }
        return result
    }

    fun refreshTodayCode() {
        adapter?.updateTodayCode()
    }

    fun scrollToTop() {

        adapter?.updateTodayCode() //změna dnes, včera hlaviček

        binding.recentsList.post {
            val lm = binding.recentsList.layoutManager
            when (lm) {
                is LinearLayoutManager ->
                    lm.scrollToPositionWithOffset(0, 0)
                else ->
                    binding.recentsList.scrollToPosition(0)
            }
        }
    }

}
