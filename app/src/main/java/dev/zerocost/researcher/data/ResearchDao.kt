package dev.zerocost.researcher.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ResearchDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRun(run: ResearchRunEntity)

    @Query("SELECT * FROM research_runs ORDER BY startedAtEpochMs DESC LIMIT :limit")
    suspend fun recentRuns(limit: Int = 20): List<ResearchRunEntity>

    @Query("SELECT * FROM research_runs WHERE id = :runId LIMIT 1")
    suspend fun getRun(runId: String): ResearchRunEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSubquestions(items: List<SubquestionEntity>)

    @Query("SELECT * FROM subquestions WHERE researchRunId = :runId ORDER BY priority DESC")
    suspend fun subquestionsForRun(runId: String): List<SubquestionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearch(search: SearchEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSource(source: SourceEntity)

    @Query("SELECT * FROM sources WHERE canonicalUrl = :canonicalUrl ORDER BY retrievedAtEpochMs DESC LIMIT 1")
    suspend fun sourceByCanonicalUrl(canonicalUrl: String): SourceEntity?

    @Query("SELECT * FROM sources WHERE id = :sourceId LIMIT 1")
    suspend fun sourceById(sourceId: String): SourceEntity?

    @Query("SELECT * FROM sources ORDER BY retrievedAtEpochMs DESC LIMIT :limit")
    suspend fun recentSources(limit: Int): List<SourceEntity>

    @Query("SELECT COUNT(*) FROM sources")
    suspend fun sourceCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvidence(items: List<EvidenceEntity>)

    @Query("SELECT * FROM evidence WHERE subquestionId = :subquestionId")
    suspend fun evidenceForSubquestion(subquestionId: String): List<EvidenceEntity>

    @Query("SELECT * FROM evidence WHERE id IN (:ids)")
    suspend fun evidenceByIds(ids: List<String>): List<EvidenceEntity>

    @Query(
        """
        SELECT e.* FROM evidence e
        INNER JOIN subquestions s ON e.subquestionId = s.id
        WHERE s.researchRunId = :runId
        ORDER BY CASE WHEN e.citationOrder IS NULL THEN 1 ELSE 0 END, e.citationOrder, e.id
        """
    )
    suspend fun evidenceForRun(runId: String): List<EvidenceEntity>


    @Query("SELECT * FROM searches WHERE researchRunId = :runId ORDER BY createdAtEpochMs")
    suspend fun searchesForRun(runId: String): List<SearchEntity>

    @Query(
        """
        SELECT DISTINCT src.* FROM sources src
        INNER JOIN evidence e ON e.sourceId = src.id
        INNER JOIN subquestions s ON e.subquestionId = s.id
        WHERE s.researchRunId = :runId
        ORDER BY src.retrievedAtEpochMs
        """
    )
    suspend fun sourcesForRun(runId: String): List<SourceEntity>

    @Query("SELECT * FROM claims WHERE researchRunId = :runId ORDER BY id")
    suspend fun claimsForRun(runId: String): List<ClaimEntity>

    @Query(
        """
        SELECT ce.* FROM claim_evidence ce
        INNER JOIN claims c ON ce.claimId = c.id
        WHERE c.researchRunId = :runId
        ORDER BY ce.claimId, ce.evidenceId
        """
    )
    suspend fun claimEvidenceForRun(runId: String): List<ClaimEvidenceEntity>

    @Query("UPDATE evidence SET citationOrder = :citationOrder WHERE id = :evidenceId")
    suspend fun setCitationOrder(evidenceId: String, citationOrder: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertClaims(claims: List<ClaimEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertClaimEvidence(edges: List<ClaimEvidenceEntity>)

    @Query(
        "DELETE FROM claim_evidence WHERE claimId IN " +
            "(SELECT id FROM claims WHERE researchRunId = :runId)"
    )
    suspend fun deleteClaimEvidenceForRun(runId: String)

    @Query("DELETE FROM claims WHERE researchRunId = :runId")
    suspend fun deleteClaimsForRun(runId: String)

    @Query(
        "UPDATE evidence SET citationOrder = NULL WHERE subquestionId IN " +
            "(SELECT id FROM subquestions WHERE researchRunId = :runId)"
    )
    suspend fun clearCitationOrderForRun(runId: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBudgetIfAbsent(budget: ProviderBudgetEntity)

    @Query(
        """
        UPDATE provider_budgets
        SET consumed = consumed + :amount, hardLimit = :hardLimit
        WHERE provider = :provider
          AND billingPeriod = :billingPeriod
          AND enabled = 1
          AND consumed + :amount <= :hardLimit
        """
    )
    suspend fun reserveProviderCredits(
        provider: String,
        billingPeriod: String,
        amount: Int,
        hardLimit: Int,
    ): Int

    @Query(
        """
        SELECT * FROM provider_budgets
        WHERE provider = :provider AND billingPeriod = :billingPeriod
        LIMIT 1
        """
    )
    suspend fun getBudget(provider: String, billingPeriod: String): ProviderBudgetEntity?
}
