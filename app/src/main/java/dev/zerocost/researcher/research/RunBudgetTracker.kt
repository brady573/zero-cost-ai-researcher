package dev.zerocost.researcher.research

class RunBudgetTracker(private val budget: ResearchBudget) {
    private val startedAt = System.currentTimeMillis()

    var searches: Int = 0
        private set
    var fetchedSources: Int = 0
        private set
    var modelCalls: Int = 0
        private set

    fun consumeSearch() {
        if (searches >= budget.maxSearches) throw ResearchBudgetExceeded("search budget")
        searches++
    }

    fun consumeSource() {
        if (fetchedSources >= budget.maxFetchedSources) {
            throw ResearchBudgetExceeded("source budget")
        }
        fetchedSources++
    }

    fun consumeModelCall() {
        if (modelCalls >= budget.maxModelCalls) throw ResearchBudgetExceeded("model-call budget")
        modelCalls++
    }

    fun canExplore(): Boolean =
        searches < budget.maxSearches &&
            fetchedSources < budget.maxFetchedSources &&
            modelCalls + FINAL_CALLS_RESERVED + ITERATION_MODEL_CALLS <= budget.maxModelCalls

    fun hasModelCall(): Boolean = modelCalls < budget.maxModelCalls

    fun canSearch(): Boolean = searches < budget.maxSearches

    fun canFetch(): Boolean = fetchedSources < budget.maxFetchedSources

    fun softDurationExceeded(): Boolean =
        System.currentTimeMillis() - startedAt > budget.maxDurationMs

    companion object {
        private const val FINAL_CALLS_RESERVED = 2
        private const val ITERATION_MODEL_CALLS = 2
    }
}

class ResearchBudgetExceeded(val budgetName: String) : Exception("$budgetName reached")
