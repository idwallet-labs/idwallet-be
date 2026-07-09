package com.cyjoon68.idwallet

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.validation.constraints.NotBlank
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import org.springframework.dao.EmptyResultDataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.bind.annotation.RestController

@RestController
@CrossOrigin(origins = ["*"])
@RequestMapping("/api")
class WalletController(private val service: WalletService) {
    @GetMapping("/wallet/credentials")
    fun credentials(): List<WalletCredential> = service.credentials()

    @PostMapping("/submission-requests")
    fun createSubmission(@RequestBody request: CreateSubmissionRequest): SubmissionRequest = service.createSubmission(request)

    @GetMapping("/submission-requests/{id}")
    fun submission(@PathVariable id: String): SubmissionRequest = service.submission(id)

    @PostMapping("/submission-requests/{id}/responses")
    fun approve(@PathVariable id: String, @RequestBody request: SubmissionResponseRequest): SubmissionResponse = service.approve(id, request)
}

@RestControllerAdvice
class ApiExceptionHandler {
    @ExceptionHandler(IllegalArgumentException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun illegalArgument(error: IllegalArgumentException): ErrorResponse = ErrorResponse(error.message ?: "bad request")
}

@Service
class WalletService(private val store: WalletStore) {
    fun credentials(): List<WalletCredential> = store.credentials()
    fun auditEvents(): List<AuditEvent> = store.auditEvents()

    fun createSubmission(request: CreateSubmissionRequest): SubmissionRequest {
        val submission = SubmissionRequest("sub-${UUID.randomUUID()}", request.requestedTypes, "PENDING", Instant.now().plusSeconds(600))
        store.saveSubmission(submission)
        audit("SUBMISSION_REQUEST_CREATED", submission.id, "requestedTypes=${request.requestedTypes.joinToString(",")}")
        return submission
    }

    fun submission(id: String): SubmissionRequest = store.findSubmission(id)
        ?: throw IllegalArgumentException("submission request not found")

    fun approve(id: String, request: SubmissionResponseRequest): SubmissionResponse {
        val now = Instant.now()
        val submission = submission(id)
        val credential = store.findCredential(request.credentialId)
            ?: throw IllegalArgumentException("credential not found")

        require(submission.status == "PENDING") { "submission is already processed" }
        require(submission.expiresAt.isAfter(now)) { "submission request expired" }
        require(credential.status == "ACTIVE") { "credential is not active" }
        require(credential.expiresAt.isAfter(LocalDate.now())) { "credential expired" }
        require(submission.requestedTypes.contains(credential.type)) { "credential type does not match request" }
        require(!store.responseExists(id)) { "duplicate submission response" }

        val approved = submission.copy(status = "APPROVED")
        val response = SubmissionResponse("resp-$id", id, request.credentialId, "APPROVED", now)
        store.updateSubmission(approved)
        store.saveResponse(response)
        audit("SUBMISSION_APPROVED", id, "credentialId=${credential.id};type=${credential.type}")
        return response
    }

    private fun audit(type: String, targetId: String, detail: String) {
        store.saveAudit(AuditEvent("audit-${UUID.randomUUID()}", type, targetId, detail, Instant.now()))
    }
}

interface WalletStore {
    fun credentials(): List<WalletCredential>
    fun findCredential(id: String): WalletCredential?
    fun saveSubmission(submission: SubmissionRequest)
    fun findSubmission(id: String): SubmissionRequest?
    fun updateSubmission(submission: SubmissionRequest)
    fun responseExists(requestId: String): Boolean
    fun saveResponse(response: SubmissionResponse)
    fun saveAudit(event: AuditEvent)
    fun auditEvents(): List<AuditEvent>
}

@Service
class JdbcWalletStore(private val jdbcTemplate: JdbcTemplate) : WalletStore {
    private val objectMapper = ObjectMapper()
    private val requestedTypesType = object : TypeReference<List<String>>() {}

    override fun credentials(): List<WalletCredential> = jdbcTemplate.query(
        "select id, type, issuer_name, payload_hash, status, expires_at from credential order by id",
    ) { rs, _ -> credential(rs) }

    override fun findCredential(id: String): WalletCredential? = findOne {
        jdbcTemplate.queryForObject(
            "select id, type, issuer_name, payload_hash, status, expires_at from credential where id = ?",
            { rs, _ -> credential(rs) },
            id,
        )
    }

    override fun saveSubmission(submission: SubmissionRequest) {
        jdbcTemplate.update(
            "insert into submission_request (id, requested_types, status, expires_at) values (?, ?::jsonb, ?, ?)",
            submission.id,
            objectMapper.writeValueAsString(submission.requestedTypes),
            submission.status,
            Timestamp.from(submission.expiresAt),
        )
    }

    override fun findSubmission(id: String): SubmissionRequest? = findOne {
        jdbcTemplate.queryForObject(
            "select id, requested_types, status, expires_at from submission_request where id = ?",
            { rs, _ -> submission(rs) },
            id,
        )
    }

    override fun updateSubmission(submission: SubmissionRequest) {
        jdbcTemplate.update("update submission_request set status = ? where id = ?", submission.status, submission.id)
    }

    override fun responseExists(requestId: String): Boolean {
        return jdbcTemplate.queryForObject(
            "select count(*) from submission_response where request_id = ?",
            Int::class.java,
            requestId,
        ) != 0
    }

    override fun saveResponse(response: SubmissionResponse) {
        jdbcTemplate.update(
            "insert into submission_response (id, request_id, credential_id, result, created_at) values (?, ?, ?, ?, ?)",
            response.id,
            response.requestId,
            response.credentialId,
            response.result,
            Timestamp.from(response.createdAt),
        )
    }

    override fun saveAudit(event: AuditEvent) {
        jdbcTemplate.update(
            "insert into audit_event (id, type, target_id, detail, created_at) values (?, ?, ?, ?, ?)",
            event.id,
            event.type,
            event.targetId,
            event.detail,
            Timestamp.from(event.createdAt),
        )
    }

    override fun auditEvents(): List<AuditEvent> = jdbcTemplate.query(
        "select id, type, target_id, detail, created_at from audit_event order by created_at, id",
    ) { rs, _ ->
        AuditEvent(
            rs.getString("id"),
            rs.getString("type"),
            rs.getString("target_id"),
            rs.getString("detail"),
            rs.getTimestamp("created_at").toInstant(),
        )
    }

    private fun credential(rs: ResultSet): WalletCredential = WalletCredential(
        rs.getString("id"),
        rs.getString("type"),
        rs.getString("issuer_name"),
        rs.getString("payload_hash"),
        rs.getString("status"),
        rs.getObject("expires_at", LocalDate::class.java),
    )

    private fun submission(rs: ResultSet): SubmissionRequest = SubmissionRequest(
        rs.getString("id"),
        objectMapper.readValue(rs.getString("requested_types"), requestedTypesType),
        rs.getString("status"),
        rs.getTimestamp("expires_at").toInstant(),
    )

    private fun <T> findOne(query: () -> T): T? = try {
        query()
    } catch (_: EmptyResultDataAccessException) {
        null
    }
}

class InMemoryWalletStore : WalletStore {
    private val credentials = listOf(
        WalletCredential("wallet-vc-1", "교육 수료 증명", "IDWallet Demo Issuer", "hash_education_1001", "ACTIVE", LocalDate.parse("2027-12-31")),
        WalletCredential("wallet-vc-2", "재직 증명", "IDWallet Demo Issuer", "hash_employment_1002", "ACTIVE", LocalDate.parse("2026-10-31")),
    )
    private val submissions = mutableMapOf<String, SubmissionRequest>()
    private val responses = mutableMapOf<String, SubmissionResponse>()
    private val auditEvents = mutableListOf<AuditEvent>()

    override fun credentials(): List<WalletCredential> = credentials
    override fun findCredential(id: String): WalletCredential? = credentials.firstOrNull { it.id == id }
    override fun saveSubmission(submission: SubmissionRequest) {
        submissions[submission.id] = submission
    }
    override fun findSubmission(id: String): SubmissionRequest? = submissions[id]
    override fun updateSubmission(submission: SubmissionRequest) {
        submissions[submission.id] = submission
    }
    override fun responseExists(requestId: String): Boolean = responses.containsKey(requestId)
    override fun saveResponse(response: SubmissionResponse) {
        responses[response.requestId] = response
    }
    override fun saveAudit(event: AuditEvent) {
        auditEvents.add(event)
    }
    override fun auditEvents(): List<AuditEvent> = auditEvents
}

data class WalletCredential(val id: String, val type: String, val issuerName: String, val payloadHash: String, val status: String, val expiresAt: LocalDate)
data class CreateSubmissionRequest(val requestedTypes: List<@NotBlank String>)
data class SubmissionRequest(val id: String, val requestedTypes: List<String>, val status: String, val expiresAt: Instant)
data class SubmissionResponseRequest(@field:NotBlank val credentialId: String)
data class SubmissionResponse(val id: String, val requestId: String, val credentialId: String, val result: String, val createdAt: Instant)
data class AuditEvent(val id: String, val type: String, val targetId: String, val detail: String, val createdAt: Instant)
data class ErrorResponse(val message: String)
