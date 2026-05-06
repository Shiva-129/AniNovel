package tachiyomi.data.handlers.novel

import app.cash.sqldelight.ExecutableQuery
import app.cash.sqldelight.Query
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import app.cash.sqldelight.db.SqlDriver
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import tachiyomi.data.Database

class AndroidNovelDatabaseHandler(
    val db: Database,
    private val driver: SqlDriver,
    val queryDispatcher: CoroutineDispatcher = Dispatchers.IO,
    val transactionDispatcher: CoroutineDispatcher = queryDispatcher,
) : NovelDatabaseHandler {

    override suspend fun <T> await(inTransaction: Boolean, block: suspend Database.() -> T): T =
        withContext(queryDispatcher) { block(db) }

    override suspend fun <T : Any> awaitList(
        inTransaction: Boolean,
        block: suspend Database.() -> Query<T>,
    ): List<T> = withContext(queryDispatcher) { block(db).executeAsList() }

    override suspend fun <T : Any> awaitOne(
        inTransaction: Boolean,
        block: suspend Database.() -> Query<T>,
    ): T = withContext(queryDispatcher) { block(db).executeAsOne() }

    override suspend fun <T : Any> awaitOneOrNull(
        inTransaction: Boolean,
        block: suspend Database.() -> Query<T>,
    ): T? = withContext(queryDispatcher) { block(db).executeAsOneOrNull() }

    override suspend fun <T : Any> awaitOneExecutable(
        inTransaction: Boolean,
        block: suspend Database.() -> ExecutableQuery<T>,
    ): T = withContext(queryDispatcher) { block(db).executeAsOne() }

    override fun <T : Any> subscribeToList(block: Database.() -> Query<T>): Flow<List<T>> =
        block(db).asFlow().mapToList(queryDispatcher)

    override fun <T : Any> subscribeToOneOrNull(block: Database.() -> Query<T>): Flow<T?> =
        block(db).asFlow().mapToOneOrNull(queryDispatcher)
}
