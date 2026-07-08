package com.cyjoon68.idwallet

import jakarta.validation.constraints.NotBlank
import java.time.Instant
import java.time.LocalDate
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@CrossOrigin(origins = ["*"])
@RequestMapping("/api")
class WalletController(private val service: WalletService = WalletService()) {
    @GetMapping("/wallet/credentials")
    fun credentials(): List<WalletCredential> = service.credentials()

    @PostMapping("/submission-requests")
    fun createSubmission(@RequestBody request: CreateSubmissionRequest): SubmissionRequest = service.createSubmission(request)

    @GetMapping("/submission-requests/{id}")
    fun submission(@PathVariable id: String): SubmissionRequest = service.submission(id)

    @PostMapping("/submission-requests/{id}/responses")
    fun approve(@PathVariable id: String, @RequestBody request: SubmissionResponseRequest): SubmissionResponse = service.approve(id, request)
}

class WalletService {
    private val credentials = listOf(
        WalletCredential("wallet-vc-1", "교육 수료 증명", "IDWallet Demo Issuer", "hash_education_1001", "ACTIVE", LocalDate.parse("2027-12-31")),
        WalletCredential("wallet-vc-2", "재직 증명", "IDWallet Demo Issuer", "hash_employment_1002", "ACTIVE", LocalDate.parse("2026-10-31")),
    )
    private val submissions = mutableMapOf<String, SubmissionRequest>()

    fun credentials(): List<WalletCredential> = credentials

    fun createSubmission(request: CreateSubmissionRequest): SubmissionRequest {
        val submission = SubmissionRequest("sub-${submissions.size + 1}", request.requestedTypes, "PENDING", Instant.now().plusSeconds(600))
        submissions[submission.id] = submission
        return submission
    }

    fun submission(id: String): SubmissionRequest = submissions[id] ?: SubmissionRequest(id, listOf("교육 수료 증명"), "PENDING", Instant.now().plusSeconds(600))

    fun approve(id: String, request: SubmissionResponseRequest): SubmissionResponse {
        val submission = submission(id).copy(status = "APPROVED")
        submissions[id] = submission
        return SubmissionResponse("resp-$id", id, request.credentialId, "APPROVED", Instant.now())
    }
}

data class WalletCredential(val id: String, val type: String, val issuerName: String, val payloadHash: String, val status: String, val expiresAt: LocalDate)
data class CreateSubmissionRequest(val requestedTypes: List<@NotBlank String>)
data class SubmissionRequest(val id: String, val requestedTypes: List<String>, val status: String, val expiresAt: Instant)
data class SubmissionResponseRequest(@field:NotBlank val credentialId: String)
data class SubmissionResponse(val id: String, val requestId: String, val credentialId: String, val result: String, val createdAt: Instant)
