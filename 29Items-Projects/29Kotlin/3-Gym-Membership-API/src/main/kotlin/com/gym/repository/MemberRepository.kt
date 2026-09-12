package com.gym.repository

import com.gym.domain.Member
import com.gym.domain.Role
import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.*

interface MemberRepository {
    suspend fun findById(id: String): Member?
    suspend fun findByEmail(email: String): Member?
    suspend fun findByBadgeCode(badgeCode: String): Member?
    suspend fun create(member: Member, passwordHash: String): Member
    suspend fun verifyPassword(email: String, plainPassword: String): Boolean
    suspend fun getAll(): List<Member>
    suspend fun getPaginated(limit: Int, offset: Long): Pair<List<Member>, Long>
}

class ExposedMemberRepository : MemberRepository {

    override suspend fun findById(id: String): Member? = newSuspendedTransaction(Dispatchers.IO) {
        MembersTable.selectAll().where { MembersTable.id eq id }
            .map { it.toMember() }
            .singleOrNull()
    }

    override suspend fun findByEmail(email: String): Member? = newSuspendedTransaction(Dispatchers.IO) {
        MembersTable.selectAll().where { MembersTable.email eq email }
            .map { it.toMember() }
            .singleOrNull()
    }

    override suspend fun findByBadgeCode(badgeCode: String): Member? = newSuspendedTransaction(Dispatchers.IO) {
        MembersTable.selectAll().where { MembersTable.badgeCode eq badgeCode }
            .map { it.toMember() }
            .singleOrNull()
    }

    override suspend fun create(member: Member, passwordHash: String): Member = newSuspendedTransaction(Dispatchers.IO) {
        val now = Clock.System.now()
        MembersTable.insert {
            it[id] = member.id
            it[email] = member.email
            it[this.passwordHash] = passwordHash
            it[fullName] = member.fullName
            it[badgeCode] = member.badgeCode
            it[role] = member.role
            it[createdAt] = now
            it[updatedAt] = now
        }
        member.copy(createdAt = now, updatedAt = now)
    }

    override suspend fun verifyPassword(email: String, plainPassword: String): Boolean = newSuspendedTransaction(Dispatchers.IO) {
        val hash = MembersTable.selectAll()
            .where { MembersTable.email eq email }
            .map { it[MembersTable.passwordHash] }
            .singleOrNull() ?: return@newSuspendedTransaction false


        try {
            org.mindrot.jbcrypt.BCrypt.checkpw(plainPassword, hash)
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun getAll(): List<Member> = newSuspendedTransaction(Dispatchers.IO) {
        MembersTable.selectAll()
            .orderBy(MembersTable.createdAt to SortOrder.DESC)
            .map { it.toMember() }
    }

    override suspend fun getPaginated(limit: Int, offset: Long): Pair<List<Member>, Long> = newSuspendedTransaction(Dispatchers.IO) {
        val total = MembersTable.selectAll().count()
        val items = MembersTable.selectAll()
            .orderBy(MembersTable.createdAt to SortOrder.DESC)
            .limit(limit)
            .offset(offset)
            .map { it.toMember() }
        Pair(items, total)
    }

    private fun ResultRow.toMember() = Member(
        id = this[MembersTable.id],
        email = this[MembersTable.email],
        fullName = this[MembersTable.fullName],
        badgeCode = this[MembersTable.badgeCode],
        role = this[MembersTable.role],
        createdAt = this[MembersTable.createdAt],
        updatedAt = this[MembersTable.updatedAt]
    )
}

