package com.stackapp.stack.offbalance

import org.junit.Assert.*
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64

class ReviewerAccessTest {
    private val keys=KeyPairGenerator.getInstance("EC").apply{initialize(ECGenParameterSpec("secp256r1"))}.generateKeyPair()
    private val publicKey=Base64.getEncoder().encodeToString(keys.public.encoded)
    private fun sign(message:String="Stack local review access v1")=Base64.getUrlEncoder().withoutPadding().encodeToString(
        Signature.getInstance("SHA256withECDSA").run{initSign(keys.private);update(message.toByteArray());sign()})
    @Test fun reusableSignedAccessWorksWithoutExpiry(){val code=sign();assertTrue(verifyReviewerCode(code,publicKey));assertTrue(verifyReviewerCode("  $code\n",publicKey))}
    @Test fun missingMalformedAndOversizedCodesFailClosed(){listOf("","invalid","-".repeat(90),"a".repeat(500),"☃".repeat(90)).forEach{assertFalse(verifyReviewerCode(it,publicKey))}}
    @Test fun alteredSignatureCannotGrantAccess(){val code=sign();val changed=(if(code.first()=='A')"B" else "A")+code.drop(1);assertFalse(verifyReviewerCode(changed,publicKey))}
    @Test fun codeForDifferentPurposeCannotGrantAccess(){assertFalse(verifyReviewerCode(sign("real purchase"),publicKey))}
    @Test fun anotherSigningKeyCannotGrantAccess(){val other=KeyPairGenerator.getInstance("EC").apply{initialize(256)}.generateKeyPair();assertFalse(verifyReviewerCode(sign(),Base64.getEncoder().encodeToString(other.public.encoded)))}
    @Test fun missingOrMalformedPublicKeyFailsClosed(){assertFalse(verifyReviewerCode(sign(),""));assertFalse(verifyReviewerCode(sign(),"not a key"))}
}
