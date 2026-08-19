package com.lymph.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lymph.data.dao.BowelDao
import com.lymph.data.dao.PeriodDao
import com.lymph.data.dao.WeightDao
import com.lymph.data.database.LymphDatabase
import com.lymph.data.entity.BowelRecord
import com.lymph.data.entity.PeriodRecord
import com.lymph.data.entity.WeightRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

actual class BiometricViewModel actual constructor(
    private val weightDao: WeightDao,
    private val periodDao: PeriodDao,
    private val bowelDao: BowelDao,
    actual val database: LymphDatabase
) {

    // Private scope for fire-and-forget DAO writes (replaces deprecated GlobalScope).
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var _weights: List<WeightRecord> by mutableStateOf(emptyList())
    actual val weights: List<WeightRecord> get() = _weights

    private var _periods: List<PeriodRecord> by mutableStateOf(emptyList())
    actual val periods: List<PeriodRecord> get() = _periods

    private var _bowels: List<BowelRecord> by mutableStateOf(emptyList())
    actual val bowels: List<BowelRecord> get() = _bowels

    actual fun startObserving(scope: CoroutineScope) {
        scope.launch { weightDao.getAll().collect { _weights = it } }
        scope.launch { periodDao.getAll().collect { _periods = it } }
        scope.launch { bowelDao.getAll().collect { _bowels = it } }
    }

    actual fun setWeights(records: List<WeightRecord>) { _weights = records }
    actual fun setPeriods(records: List<PeriodRecord>) { _periods = records }
    actual fun setBowels(records: List<BowelRecord>) { _bowels = records }

    actual fun insertWeight(record: WeightRecord) {
        ioScope.launch { weightDao.insert(record) }
    }
    actual fun insertPeriod(record: PeriodRecord) {
        ioScope.launch { periodDao.insert(record) }
    }
    actual fun insertBowel(record: BowelRecord) {
        ioScope.launch { bowelDao.insert(record) }
    }
    actual fun deleteWeight(id: String) {
        ioScope.launch { weightDao.delete(id) }
    }
    actual fun deletePeriod(id: String) {
        ioScope.launch { periodDao.delete(id) }
    }
    actual fun deleteBowel(id: String) {
        ioScope.launch { bowelDao.delete(id) }
    }
}
