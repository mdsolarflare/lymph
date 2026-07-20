package com.lymph.ui.viewmodel

import com.lymph.data.database.LymphDatabase
import com.lymph.data.entity.BowelRecord
import com.lymph.data.entity.PeriodRecord
import com.lymph.data.entity.WeightRecord
import kotlinx.coroutines.CoroutineScope

/**
 * Expect declaration for the biometric ViewModel.
 * Aggregates all three biometric streams and exposes reactive lists plus mutation functions.
 *
 * Actual implementations:
 * - nativeMain: Uses Room DAOs with viewModelScope for lifecycle-aware collection.
 * - wasmJsMain: Uses in-memory stub database with Compose state and manual scope observation.
 *
 * Note: Properties are declared in the expect class body so commonMain code can access them.
 * Actual implementations define these properties without 'actual' keyword (they just implement the contract).
 * Methods are declared in the expect class and implemented in actual implementations.
 */
expect class BiometricViewModel(
    weightDao: com.lymph.data.dao.WeightDao,
    periodDao: com.lymph.data.dao.PeriodDao,
    bowelDao: com.lymph.data.dao.BowelDao,
    database: LymphDatabase
) {

    val database: LymphDatabase

    val weights: List<WeightRecord>
    val periods: List<PeriodRecord>
    val bowels: List<BowelRecord>

    /** Updates the in-memory lists from the DAO flows. Called from a Compose scope. */
    fun startObserving(scope: CoroutineScope)

    /** Internal: refreshes weight list from the latest flow emission. */
    fun setWeights(records: List<WeightRecord>)
    fun setPeriods(records: List<PeriodRecord>)
    fun setBowels(records: List<BowelRecord>)

    fun insertWeight(record: WeightRecord)
    fun insertPeriod(record: PeriodRecord)
    fun insertBowel(record: BowelRecord)

    fun deleteWeight(id: String)
    fun deletePeriod(id: String)
    fun deleteBowel(id: String)
}
