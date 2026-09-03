package dev.zerocost.researcher.data

import androidx.room.Entity
import androidx.room.Index

@Entity(tableName = "research_runs", primaryKeys = ["id"])
data class ResearchRunEntity(
    val id: String,
    val userQuery: String,
    val status: String,
    val mode: String,
    val startedAtEpochMs: Long,
    val completedAtEpochMs: Long?,
    val stopReason: String?,
    val answerText: String?,
    val confidence: Double?,
)

@Entity(tableName = "subquestions", primaryKeys = ["id"], indices = [Index("researchRunId")])
data class SubquestionEntity(
    val id: String,
    val researchRunId: String,
    val question: String,
    val priority: Double,
    val status: String,
)

@Entity(
    tableName = "searches",
    primaryKeys = ["id"],
    indices = [Index("researchRunId"), Index("subquestionId")],
)
data class SearchEntity(
    val id: String,
    val researchRunId: String,
    val subquestionId: String?,
    val provider: String,
    val query: String,
    val createdAtEpochMs: Long,
)

@Entity(
    tableName = "sources",
    primaryKeys = ["id"],
    indices = [Index("canonicalUrl"), Index("domain"), Index("contentHash")],
)
data class SourceEntity(
    val id: String,
    val canonicalUrl: String,
    val originalUrl: String,
    val domain: String,
    val publisher: String?,
    val title: String,
    val publishedAtEpochMs: Long?,
    val retrievedAtEpochMs: Long,
    val contentHash: String,
    val htmlPath: String,
    val textPath: String,
)

@Entity(
    tableName = "evidence",
    primaryKeys = ["id"],
    indices = [Index("sourceId"), Index("subquestionId"), Index("claimKey")],
)
data class EvidenceEntity(
    val id: String,
    val sourceId: String,
    val subquestionId: String?,
    val claimKey: String,
    val claimCandidate: String,
    val excerpt: String,
    val section: String?,
    val relevanceScore: Double,
    val authorityScore: Double,
    val primarySourceScore: Double,
    val sourceType: String,
    val relationship: String,
    val publishedAtEpochMs: Long?,
    val citationOrder: Int?,
)

@Entity(tableName = "claims", primaryKeys = ["id"], indices = [Index("researchRunId")])
data class ClaimEntity(
    val id: String,
    val researchRunId: String,
    val claimText: String,
    val confidence: Double,
    val status: String,
)

@Entity(tableName = "claim_evidence", primaryKeys = ["claimId", "evidenceId"])
data class ClaimEvidenceEntity(
    val claimId: String,
    val evidenceId: String,
    val relationship: String,
    val strength: Double,
)

@Entity(tableName = "provider_budgets", primaryKeys = ["provider", "billingPeriod"])
data class ProviderBudgetEntity(
    val provider: String,
    val billingPeriod: String,
    val hardLimit: Int,
    val consumed: Int,
    val enabled: Boolean,
)
