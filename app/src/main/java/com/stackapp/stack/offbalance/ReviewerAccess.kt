package com.stackapp.stack.offbalance

import android.content.Context
import com.stackapp.stack.BuildConfig
import com.stackapp.stack.R
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/** The signed code grants only isolated sample content, never a real entitlement. */
fun verifyReviewerCode(code:String,publicKey:String):Boolean {
    val value=code.trim()
    if(value.length !in 80..104 || !value.all{it.isLetterOrDigit() || it=='-' || it=='_'})return false
    return runCatching {
        val key=KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(publicKey)))
        Signature.getInstance("SHA256withECDSA").run {
            initVerify(key)
            update("Stack local review access v1".toByteArray(Charsets.UTF_8))
            verify(Base64.getUrlDecoder().decode(value))
        }
    }.getOrDefault(false)
}

object ReviewerAccess {
    const val EXTRA_CODE="review_access_code"
    @Volatile var testVerifier:((String)->Boolean)?=null
    fun valid(context:Context,code:String):Boolean =
        if(BuildConfig.DEBUG && testVerifier!=null)testVerifier!!(code)
        else verifyReviewerCode(code,context.getString(R.string.review_access_public_key))
}
