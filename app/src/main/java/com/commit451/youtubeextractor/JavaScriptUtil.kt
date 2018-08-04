package com.commit451.youtubeextractor

import org.mozilla.javascript.Context
import org.mozilla.javascript.Function

/**
 * Runs JavaScripty things
 */
internal object JavaScriptUtil {

    private const val DECRYPTION_FUNC_NAME = "decrypt"

    fun loadDecryptionCode(playerCode: String): String {
        val decryptionFuncName: String = Util.matchGroup("([\"\\'])signature\\1\\s*,\\s*([a-zA-Z0-9$]+)\\(", playerCode, 2)
        var callerFunc = "function $DECRYPTION_FUNC_NAME(a){return %%(a);}"

        val functionPattern = ("("
                + decryptionFuncName.replace("$", "\\$")
                + "=function\\([a-zA-Z0-9_]+\\)\\{.+?\\})")

        val decryptionFunc = "var " + Util.matchGroup(functionPattern, playerCode, 1) + ";"

        val helperObjectName = Util
                .matchGroup(";([A-Za-z0-9_\\$]{2})\\...\\(", decryptionFunc, 1)

        val helperPattern = "(var " + helperObjectName.replace("$", "\\$") + "=\\{.+?\\}\\};)"
        val helperObject = Util.matchGroup(helperPattern, playerCode, 1)

        callerFunc = callerFunc.replace("%%", decryptionFuncName)
        return helperObject + decryptionFunc + callerFunc
    }

    fun decryptSignature(encryptedSig: String, decryptionCode: String): String {
        val context = Context.enter()
        context.optimizationLevel = -1
        val result: Any?
        try {
            val scope = context.initStandardObjects()
            context.evaluateString(scope, decryptionCode, "decryptionCode", 1, null)
            val decryptionFunc = scope.get("decrypt", scope) as Function
            result = decryptionFunc.call(context, scope, scope, arrayOf<Any>(encryptedSig))
        } catch (e: Exception) {
            throw e
        } finally {
            Context.exit()
        }
        return result?.toString() ?: ""
    }
}