package dev.zerocost.researcher.data

import dev.zerocost.researcher.research.*
import java.util.UUID

class ResearchRepository(private val dao: ResearchDao) {
    suspend fun startRun(query: String, mode: ResearchMode): String {
        val id = UUID.randomUUID().toString()
        dao.upsertRun(
            ResearchRunEntity(
                id = id,
                userQuery = query,
                status = ResearchState.PLANNING.name,
                mode = mode.name,
                startedAtEpochMs = System.currentTimeMillis(),
                completedAtEpochMs = null,
                stopReason = null,
                answerText = null,
                confidence = null,
            )
        )
        return id
    }

    suspend fun updateRunState(runId: String, state: ResearchState) {
        dao.getRun(runId)?.let { dao.upsertRun(it.copy(status = state.name)) }
    }

    suspend fun reopenRun(runId: String) {
        dao.deleteClaimEvidenceForRun(runId)
        dao.deleteClaimsForRun(runId)
        dao.clearCitationOrderForRun(runId)
        dao.getRun(runId)?.let {
            dao.upsertRun(
                it.copy(
                    status = ResearchState.PLANNING.name,
                    completedAtEpochMs = null,
                    stopReason = null,
                    answerText = null,
                    confidence = null,
                )
            )
        }
    }

    suspend fun completeRun(
        runId: String,
        answer: String,
        stopReason: String,
        confidence: Double?,
    ) {
        dao.getRun(runId)?.let {
            dao.upsertRun(
                it.copy(
                    status = ResearchState.COMPLETE.name,
                    completedAtEpochMs = System.currentTimeMillis(),
                    stopReason = stopReason,
                    answerText = answer,
                    confidence = confidence,
                )
            )
        }
    }

    suspend fun failRun(runId: String, state: ResearchState, reason: String) {
        dao.getRun(runId)?.let {
            dao.upsertRun(
                it.copy(
                    status = state.name,
                    completedAtEpochMs = System.currentTimeMillis(),
                    stopReason = reason,
                )
            )
        }
    }

    suspend fun subquestionsForRun(runId: String): List<PlannedSubquestion> =
        dao.subquestionsForRun(runId).map {
            PlannedSubquestion(
                id = it.id,
                question = it.question,
                priority = it.priority,
                status = it.status,
            )
        }

    suspend fun saveSubquestions(runId: String, subquestions: List<PlannedSubquestion>) {
        dao.upsertSubquestions(
            subquestions.map {
                SubquestionEntity(it.id, runId, it.question, it.priority, it.status)
            }
        )
    }

    suspend fun recordSearch(runId: String, subquestionId: String?, provider: String, query: String) {
        dao.insertSearch(
            SearchEntity(
                id = UUID.randomUUID().toString(),
                researchRunId = runId,
                subquestionId = subquestionId,
                provider = provider,
                query = query,
                createdAtEpochMs = System.currentTimeMillis(),
            )
        )
    }

    suspend fun savePage(page: RetrievedPage) {
        dao.upsertSource(
            SourceEntity(
                id = page.sourceId,
                canonicalUrl = page.canonicalUrl,
                originalUrl = page.url,
                domain = page.domain,
                publisher = page.publisher,
                title = page.title,
                publishedAtEpochMs = page.publishedAtEpochMs,
                retrievedAtEpochMs = System.currentTimeMillis(),
                contentHash = page.contentHash,
                htmlPath = page.htmlPath,
                textPath = page.textPath,
            )
        )
    }

    suspend fun cachedSource(canonicalUrl: String): SourceEntity? =
        dao.sourceByCanonicalUrl(canonicalUrl)

    suspend fun sourceById(sourceId: String): SourceEntity? = dao.sourceById(sourceId)

    suspend fun saveEvidence(items: List<EvidenceItem>) {
        if (items.isEmpty()) return
        dao.insertEvidence(
            items.map {
                EvidenceEntity(
                    id = it.id,
                    sourceId = it.sourceId,
                    subquestionId = it.subquestionId,
                    claimKey = it.claimKey,
                    claimCandidate = it.claimCandidate,
                    excerpt = it.supportingExcerpt,
                    section = it.section,
                    relevanceScore = it.relevance,
                    authorityScore = it.authority,
                    primarySourceScore = it.primarySource,
                    sourceType = it.sourceType.name,
                    relationship = it.relationship.name,
                    publishedAtEpochMs = it.publishedAtEpochMs,
                    citationOrder = null,
                )
            }
        )
    }

    suspend fun saveCitationOrder(evidenceIds: List<String>) {
        evidenceIds.forEachIndexed { index, evidenceId ->
            dao.setCitationOrder(evidenceId, index)
        }
    }

    suspend fun saveClaims(runId: String, claims: List<MaterialClaim>) {
        dao.upsertClaims(
            claims.map {
                ClaimEntity(
                    id = it.id,
                    researchRunId = runId,
                    claimText = it.claimText,
                    confidence = 0.0,
                    status = "DRAFT",
                )
            }
        )
        val evidenceById = evidenceByIds(claims.flatMap { it.evidenceIds }.distinct())
            .associateBy { it.id }
        dao.upsertClaimEvidence(
            claims.flatMap { claim ->
                claim.evidenceIds.mapNotNull { evidenceId ->
                    val evidence = evidenceById[evidenceId] ?: return@mapNotNull null
                    ClaimEvidenceEntity(
                        claimId = claim.id,
                        evidenceId = evidenceId,
                        relationship = evidence.relationship.name,
                        strength = evidence.relevance,
                    )
                }
            }
        )
    }

    suspend fun saveVerification(
        runId: String,
        claims: List<MaterialClaim>,
        verifications: List<CitationVerification>,
    ) {
        val byClaim = verifications.associateBy { it.claimId }
        dao.upsertClaims(
            claims.map { claim ->
                val status = byClaim[claim.id]?.status ?: VerificationStatus.UNSUPPORTED
                ClaimEntity(
                    id = claim.id,
                    researchRunId = runId,
                    claimText = claim.claimText,
                    confidence = when (status) {
                        VerificationStatus.SUPPORTED -> 1.0
                        VerificationStatus.PARTIALLY_SUPPORTED -> 0.5
                        VerificationStatus.CONTRADICTED -> 0.1
                        VerificationStatus.UNSUPPORTED -> 0.0
                    },
                    status = status.name,
                )
            }
        )
    }

    suspend fun evidenceForSubquestion(subquestionId: String): List<EvidenceItem> =
        dao.evidenceForSubquestion(subquestionId).map(::toDomain)

    suspend fun evidenceForRun(runId: String): List<EvidenceItem> =
        dao.evidenceForRun(runId).map(::toDomain)

    suspend fun evidenceByIds(ids: List<String>): List<EvidenceItem> =
        if (ids.isEmpty()) emptyList() else dao.evidenceByIds(ids).map(::toDomain)

    suspend fun recentRuns(limit: Int = 20): List<ResearchRunEntity> = dao.recentRuns(limit)

    suspend fun run(runId: String): ResearchRunEntity? = dao.getRun(runId)

    private fun toDomain(entity: EvidenceEntity): EvidenceItem = EvidenceItem(
        id = entity.id,
        sourceId = entity.sourceId,
        subquestionId = entity.subquestionId,
        claimKey = entity.claimKey,
        claimCandidate = entity.claimCandidate,
        supportingExcerpt = entity.excerpt,
        section = entity.section,
        relevance = entity.relevanceScore,
        authority = entity.authorityScore,
        primarySource = entity.primarySourceScore,
        sourceType = runCatching { SourceType.valueOf(entity.sourceType) }
            .getOrDefault(SourceType.UNKNOWN),
        relationship = runCatching { EvidenceRelationship.valueOf(entity.relationship) }
            .getOrDefault(EvidenceRelationship.CONTEXTUALIZES),
        publishedAtEpochMs = entity.publishedAtEpochMs,
    )
}
