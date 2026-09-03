package dev.zerocost.researcher.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ResearchRunEntity::class,
        SubquestionEntity::class,
        SearchEntity::class,
        SourceEntity::class,
        EvidenceEntity::class,
        ClaimEntity::class,
        ClaimEvidenceEntity::class,
        ProviderBudgetEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class ResearchDatabase : RoomDatabase() {
    abstract fun researchDao(): ResearchDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "DROP INDEX IF EXISTS index_sources_canonicalUrl"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_sources_canonicalUrl " +
                        "ON sources(canonicalUrl)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_sources_contentHash " +
                        "ON sources(contentHash)"
                )
            }
        }
    }
}
