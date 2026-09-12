package com.gym.repository

import com.gym.domain.CheckIn
import com.gym.domain.CheckInStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.Instant
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

interface CheckInRepository {
    suspend fun recordCheckIn(checkIn: CheckIn): CheckIn
    suspend fun getRecentCheckInsByMember(memberId: String, limit: Int = 10): List<CheckIn>
    suspend fun getRecentCheckInsByMemberPaginated(memberId: String, limit: Int, offset: Long): Pair<List<CheckIn>, Long>
    suspend fun getRecentCheckInByBadge(badgeCode: String): CheckIn?
    suspend fun countCheckInsSince(since: Instant): Long
}

class ExposedCheckInRepository : CheckInRepository {

    override suspend fun recordCheckIn(checkIn: CheckIn): CheckIn = newSuspendedTransaction(Dispatchers.IO) {
        CheckInsTable.insert {
            it[id] = checkIn.id
            it[memberId] = checkIn.memberId
            it[badgeCode] = checkIn.badgeCode
            it[zone] = checkIn.zone
            it[turnstileId] = checkIn.turnstileId
            it[status] = checkIn.status
            it[rejectionReason] = checkIn.rejectionReason
            it[scannedAt] = checkIn.scannedAt
        }
        checkIn
    }

    override suspend fun getRecentCheckInsByMember(memberId: String, limit: Int): List<CheckIn> = newSuspendedTransaction(Dispatchers.IO) {
        CheckInsTable.selectAll()
            .where { CheckInsTable.memberId eq memberId }
            .orderBy(CheckInsTable.scannedAt to SortOrder.DESC)
            .limit(limit)
            .map { it.toCheckIn() }
    }

    override suspend fun getRecentCheckInsByMemberPaginated(memberId: String, limit: Int, offset: Long): Pair<List<CheckIn>, Long> = newSuspendedTransaction(Dispatchers.IO) {
        val total = CheckInsTable.selectAll().where { CheckInsTable.memberId eq memberId }.count()
        val items = CheckInsTable.selectAll()
            .where { CheckInsTable.memberId eq memberId }
            .orderBy(CheckInsTable.scannedAt to SortOrder.DESC)
            .limit(limit)
            .offset(offset)
            .map { it.toCheckIn() }
        Pair(items, total)
    }


    override suspend fun getRecentCheckInByBadge(badgeCode: String): CheckIn? = newSuspendedTransaction(Dispatchers.IO) {
        CheckInsTable.selectAll()
            .where { CheckInsTable.badgeCode eq badgeCode }
            .orderBy(CheckInsTable.scannedAt to SortOrder.DESC)
            .limit(1)
            .map { it.toCheckIn() }
            .singleOrNull()
    }

    override suspend fun countCheckInsSince(since: Instant): Long = newSuspendedTransaction(Dispatchers.IO) {
        CheckInsTable.selectAll()
            .where { (CheckInsTable.scannedAt greaterEq since) and (CheckInsTable.status eq CheckInStatus.GRANTED) }
            .count()
    }

    private fun ResultRow.toCheckIn() = CheckIn(
        id = this[CheckInsTable.id],
        memberId = this[CheckInsTable.memberId],
        badgeCode = this[CheckInsTable.badgeCode],
        zone = this[CheckInsTable.zone],
        turnstileId = this[CheckInsTable.turnstileId],
        status = this[CheckInsTable.status],
        rejectionReason = this[CheckInsTable.rejectionReason],
        scannedAt = this[CheckInsTable.scannedAt]
    )
}
