package dev.zerocost.researcher.search

import dev.zerocost.researcher.data.ProviderBudgetEntity
import dev.zerocost.researcher.data.ResearchDao
import java.time.YearMonth

class ProviderBudgetController(private val dao: ResearchDao) {
    suspend fun reserve(provider: String, amount: Int, hardLimit: Int) {
        require(amount > 0)
        val period = YearMonth.now().toString()
        dao.insertBudgetIfAbsent(
            ProviderBudgetEntity(
                provider = provider,
                billingPeriod = period,
                hardLimit = hardLimit,
                consumed = 0,
                enabled = true,
            )
        )
        val rows = dao.reserveProviderCredits(provider, period, amount, hardLimit)
        if (rows != 1) {
            throw ProviderBudgetExceededException(
                "$provider local free-tier budget exhausted for $period"
            )
        }
    }

    suspend fun remaining(provider: String, hardLimit: Int): Int {
        val budget = dao.getBudget(provider, YearMonth.now().toString())
        return (hardLimit - (budget?.consumed ?: 0)).coerceAtLeast(0)
    }
}
