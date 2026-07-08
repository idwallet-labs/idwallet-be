package com.cyjoon68.idwallet

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class IdwalletBeApplicationTests {

	@Test
	fun approvesSubmissionRequest() {
		val service = WalletService()
		val submission = service.createSubmission(CreateSubmissionRequest(listOf("교육 수료 증명")))
		val response = service.approve(submission.id, SubmissionResponseRequest("wallet-vc-1"))

		assertEquals("APPROVED", response.result)
	}

	@Test
	fun exposesOnlyPayloadHash() {
		val credential = WalletService().credentials().first()

		assertEquals("hash_education_1001", credential.payloadHash)
		assertFalse(credential.payloadHash.contains("홍길동"))
	}

}
