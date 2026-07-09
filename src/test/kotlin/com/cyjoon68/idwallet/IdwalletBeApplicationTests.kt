package com.cyjoon68.idwallet

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith

class IdwalletBeApplicationTests {

	@Test
	fun approvesSubmissionRequest() {
		val service = WalletService(InMemoryWalletStore())
		val submission = service.createSubmission(CreateSubmissionRequest(listOf("교육 수료 증명")))
		val response = service.approve(submission.id, SubmissionResponseRequest("wallet-vc-1"))

		assertEquals("APPROVED", response.result)
	}

	@Test
	fun rejectsCredentialTypeMismatch() {
		val service = WalletService(InMemoryWalletStore())
		val submission = service.createSubmission(CreateSubmissionRequest(listOf("재직 증명")))

		val error = assertFailsWith<IllegalArgumentException> {
			service.approve(submission.id, SubmissionResponseRequest("wallet-vc-1"))
		}

		assertEquals("credential type does not match request", error.message)
	}

	@Test
	fun rejectsDuplicateSubmissionResponse() {
		val service = WalletService(InMemoryWalletStore())
		val submission = service.createSubmission(CreateSubmissionRequest(listOf("교육 수료 증명")))

		service.approve(submission.id, SubmissionResponseRequest("wallet-vc-1"))
		val error = assertFailsWith<IllegalArgumentException> {
			service.approve(submission.id, SubmissionResponseRequest("wallet-vc-1"))
		}

		assertEquals("submission is already processed", error.message)
	}

	@Test
	fun recordsAuditEvents() {
		val service = WalletService(InMemoryWalletStore())
		val submission = service.createSubmission(CreateSubmissionRequest(listOf("교육 수료 증명")))

		service.approve(submission.id, SubmissionResponseRequest("wallet-vc-1"))

		assertEquals(listOf("SUBMISSION_REQUEST_CREATED", "SUBMISSION_APPROVED"), service.auditEvents().map { it.type })
	}

	@Test
	fun exposesOnlyPayloadHash() {
		val credential = WalletService(InMemoryWalletStore()).credentials().first()

		assertEquals("hash_education_1001", credential.payloadHash)
		assertFalse(credential.payloadHash.contains("홍길동"))
	}

}
