package com.example

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val isLoading: Boolean = false
)

class ChatViewModel : ViewModel() {
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _currentSearchQuery = MutableStateFlow<String?>(null)
    val currentSearchQuery: StateFlow<String?> = _currentSearchQuery.asStateFlow()

    init {
        // Welcome message
        _messages.value = listOf(
            ChatMessage(text = "Hello! I'm your native AI assistant. How can I help you today?", isUser = false)
        )
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return

        val userMessage = ChatMessage(text = text, isUser = true)
        val loadingMessage = ChatMessage(text = "Searching Google for AI Overview...", isUser = false, isLoading = true)
        
        _messages.value = _messages.value + userMessage + loadingMessage
        
        // Trigger the web search in the hidden WebView
        _currentSearchQuery.value = text
    }

    fun onSearchResult(result: String) {
        if (_messages.value.lastOrNull()?.isLoading == true) {
            _messages.value = _messages.value.dropLast(1) + ChatMessage(text = result, isUser = false)
            _currentSearchQuery.value = null // reset so we can search again
        }
    }
}
