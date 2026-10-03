package com.pulsefinance.app.ui

import android.app.Application
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pulsefinance.app.core.Analytics
import com.pulsefinance.app.core.Categories
import com.pulsefinance.app.core.SmsTransactionParser
import com.pulsefinance.app.core.StoreClassifier
import com.pulsefinance.app.core.Summary
import com.pulsefinance.app.core.Transaction
import com.pulsefinance.app.core.ViewMode
import com.pulsefinance.app.data.ConfigLoader
import com.pulsefinance.app.data.SmsReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.YearMonth

data class DashboardUiState(
    val hasPermission: Boolean = false,
    val permissionDenied: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val lastRefreshedMs: Long? = null,
    val scannedMessages: Int = 0,
    val hiddenNaCount: Int = 0,
    val templateCount: Int = 0,

    // Selection
    val mode: ViewMode = ViewMode.MONTHLY,
    val year: Int = YearMonth.now().year,
    val month: Int = YearMonth.now().monthValue,
    val search: String = "",
    val categoryFilter: String? = null,
    val methodFilter: String? = null,
    val selectionInitialised: Boolean = false,

    // Data
    val allTransactions: List<Transaction> = emptyList(),

    // Derived
    val periodCount: Int = 0,
    val summary: Summary = Summary(),
    val rows: List<Transaction> = emptyList(),
    val availableYears: List<Int> = listOf(YearMonth.now().year),
    val availableCategories: List<String> = emptyList(),
    val availableMethods: List<String> = emptyList(),
)

class DashboardViewModel(app: Application) : AndroidViewModel(app) {

    private val reader = SmsReader(app)
    private var parser: SmsTransactionParser? = null

    private val _state = MutableStateFlow(DashboardUiState(hasPermission = reader.hasPermission()))
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()

    private var refreshJob: Job? = null
    private var debounceJob: Job? = null
    private var observerRegistered = false

    private val smsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = scheduleRefresh()
    }

    // ---- loading ----------------------------------------------------------------------------

    /** Re-reads the whole inbox. Called on every app open/resume and when a new SMS arrives. */
    fun refresh() {
        if (!reader.hasPermission()) {
            _state.update { it.copy(hasPermission = false, loading = false) }
            return
        }
        registerObserver()
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _state.update { it.copy(hasPermission = true, permissionDenied = false, loading = true, error = null) }
            try {
                val activeParser = parser ?: withContext(Dispatchers.Default) { buildParser() }.also { parser = it }
                val sms = withContext(Dispatchers.IO) { reader.readInbox() }
                val result = withContext(Dispatchers.Default) { activeParser.parseAll(sms) }

                val visible = result.transactions
                    .filter { !Categories.isNa(it.category) }
                    .map { it.copy(category = it.category.trim().uppercase()) }
                    .sortedByDescending { it.timestampMs }

                _state.update { old ->
                    var year = old.year
                    var month = old.month
                    // On the first load, jump to the latest month that has data.
                    if (!old.selectionInitialised && visible.isNotEmpty()) {
                        year = visible.first().year
                        month = visible.first().month
                    }
                    recompute(
                        old.copy(
                            loading = false,
                            allTransactions = visible,
                            scannedMessages = result.scannedMessages,
                            hiddenNaCount = result.transactions.size - visible.size,
                            templateCount = activeParser.templateNames.size,
                            lastRefreshedMs = System.currentTimeMillis(),
                            year = year,
                            month = month,
                            selectionInitialised = old.selectionInitialised || visible.isNotEmpty(),
                        )
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "Couldn't read messages") }
            }
        }
    }

    fun onPermissionResult(granted: Boolean) {
        if (granted) refresh()
        else _state.update { it.copy(hasPermission = false, permissionDenied = true) }
    }

    /** Overwrites a config file in internal storage and re-parses. */
    fun importConfig(name: String, json: String) {
        try {
            java.io.File(getApplication<Application>().filesDir, name).writeText(json)
            parser = null // Force rebuild with new file
            refresh()
            _state.update { it.copy(message = "Imported $name") }
        } catch (e: Exception) {
            _state.update { it.copy(error = "Import failed: ${e.message}") }
        }
    }

    /** Deletes custom configs and goes back to bundled assets. */
    fun resetConfigs() {
        val dir = getApplication<Application>().filesDir
        java.io.File(dir, "templates.json").delete()
        java.io.File(dir, "store_map.json").delete()
        parser = null
        refresh()
        _state.update { it.copy(message = "Reset to default configuration") }
    }

    private fun buildParser(): SmsTransactionParser {
        val ctx = getApplication<Application>()
        return SmsTransactionParser(
            templates = ConfigLoader.loadTemplates(ctx),
            classifier = StoreClassifier(ConfigLoader.loadStoreMap(ctx)),
        )
    }

    private fun registerObserver() {
        if (observerRegistered) return
        getApplication<Application>().contentResolver
            .registerContentObserver(Telephony.Sms.CONTENT_URI, true, smsObserver)
        observerRegistered = true
    }

    private fun scheduleRefresh() {
        debounceJob?.cancel()
        debounceJob = viewModelScope.launch {
            delay(800) // a burst of provider notifications becomes one refresh
            refresh()
        }
    }

    override fun onCleared() {
        if (observerRegistered) {
            getApplication<Application>().contentResolver.unregisterContentObserver(smsObserver)
        }
        super.onCleared()
    }

    // ---- selection --------------------------------------------------------------------------

    fun setMode(mode: ViewMode) = _state.update { recompute(it.copy(mode = mode)) }

    fun setMonth(month: Int) = _state.update { recompute(it.copy(month = month, mode = ViewMode.MONTHLY)) }

    fun setYear(year: Int) = _state.update { recompute(it.copy(year = year, mode = ViewMode.MONTHLY)) }

    fun navigateMonth(delta: Int) = _state.update { s ->
        var m = s.month + delta
        var y = s.year
        if (m < 1) { m = 12; y-- } else if (m > 12) { m = 1; y++ }
        if (y !in s.availableYears) {
            s.copy(message = "No data for ${Analytics.monthName(m)} $y")
        } else {
            recompute(s.copy(mode = ViewMode.MONTHLY, month = m, year = y))
        }
    }

    fun setSearch(q: String) = _state.update { recompute(it.copy(search = q)) }
    fun setCategoryFilter(c: String?) = _state.update { recompute(it.copy(categoryFilter = c)) }
    fun setMethodFilter(m: String?) = _state.update { recompute(it.copy(methodFilter = m)) }
    fun consumeMessage() = _state.update { it.copy(message = null) }

    // ---- derived data -----------------------------------------------------------------------

    private fun recompute(s: DashboardUiState): DashboardUiState {
        val period = Analytics.inPeriod(s.allTransactions, s.mode, s.year, s.month)
        val summary = Analytics.summarize(
            period, s.mode, s.year, s.month, parser?.templateNames ?: emptyList()
        )
        val q = s.search.trim().lowercase()
        val rows = period.filter { t ->
            (q.isEmpty() ||
                t.storeName.lowercase().contains(q) ||
                t.category.lowercase().contains(q) ||
                t.template.lowercase().contains(q)) &&
                (s.categoryFilter == null || t.category == s.categoryFilter) &&
                (s.methodFilter == null || t.template == s.methodFilter)
        }
        val years = s.allTransactions.map { it.year }.distinct().sortedDescending()
        return s.copy(
            periodCount = period.size,
            summary = summary,
            rows = rows,
            availableYears = years.ifEmpty { listOf(YearMonth.now().year) },
            availableCategories = s.allTransactions.map { it.category }.distinct().sorted(),
            availableMethods = s.allTransactions.map { it.template }.distinct().sorted(),
        )
    }
}
