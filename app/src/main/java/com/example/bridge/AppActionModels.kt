package com.example.bridge

data class ContactMatch(
    val id: String,
    val name: String,
    val phoneNumber: String,
    val typeLabel: String = "Mobile"
)

sealed class ActionResult {
    data class Success(
        val action: String,
        val message: String,
        val details: Map<String, String> = emptyMap()
    ) : ActionResult()

    data class Failure(
        val action: String,
        val reason: String,
        val message: String,
        val suggestedAlternatives: List<String> = emptyList(),
        val webFallbackUrl: String? = null,
        val playStorePackage: String? = null
    ) : ActionResult()

    data class DisambiguationNeeded(
        val action: String,
        val query: String,
        val message: String,
        val candidates: List<ContactMatch>
    ) : ActionResult()
}

data class ToolExecutionReport(
    val toolName: String,
    val parameters: Map<String, String>,
    val result: ActionResult,
    val timestamp: Long = System.currentTimeMillis()
)
