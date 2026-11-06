package com.example.voice

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Shared dynamic vocabulary manager for QuickCalc & Test Playground.
 * Ensures custom user terms (e.g., "princeniithompson@gmail.com", "30first")
 * and menu variables are dynamically injected into Gemini Live's vocabulary
 * before the WebSocket handshake across both Calculator and Playground modules.
 */
object SharedVocabularyManager {

    private val defaultUserTerms = listOf(
        "princeniithompson@gmail.com",
        "30first"
    )

    private val _userVocabulary = MutableStateFlow<List<String>>(defaultUserTerms)
    val userVocabulary: StateFlow<List<String>> = _userVocabulary.asStateFlow()

    fun addTerm(term: String) {
        val clean = term.trim()
        if (clean.isNotBlank() && !_userVocabulary.value.contains(clean)) {
            _userVocabulary.value = (_userVocabulary.value + clean).distinct()
        }
    }

    fun removeTerm(term: String) {
        _userVocabulary.value = _userVocabulary.value.filter { it != term }
    }

    fun setTerms(terms: List<String>) {
        val merged = (terms + defaultUserTerms).distinct().filter { it.isNotBlank() }
        _userVocabulary.value = merged
    }

    /**
     * Builds the complete unified vocabulary list combining menu item names and user-defined terms.
     */
    fun getUnifiedVocabulary(menuItemNames: List<String>): List<String> {
        val combined = mutableListOf<String>()
        combined.addAll(menuItemNames)
        combined.addAll(_userVocabulary.value)
        return combined.distinct().filter { it.isNotBlank() }
    }
}
