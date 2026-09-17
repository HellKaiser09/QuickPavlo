package com.example.quickpavlo.ui.feature.chat

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.quickpavlo.domain.model.ChatMessage
import com.example.quickpavlo.domain.network.P2PConnectionManager
import com.example.quickpavlo.domain.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val p2pManager: P2PConnectionManager
) : ViewModel() {

    val messages: StateFlow<List<ChatMessage>> = chatRepository.getChatHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val connectionState = p2pManager.connectionState

    fun sendMessage(text: String) {
        if (text.isNotBlank()) {
            viewModelScope.launch {
                chatRepository.sendTextMessage(text)
            }
        }
    }

    fun sendFile(uri: Uri, context: Context) {
        viewModelScope.launch {
            chatRepository.sendFileMessage(uri, context)
        }
    }

    fun resumeFileTransfer(payloadId: Long, context: Context) {
        viewModelScope.launch {
            chatRepository.resumeFileTransfer(payloadId, context)
        }
    }

    fun clearChat() {
        viewModelScope.launch {
            chatRepository.clearHistory()
        }
    }

    fun disconnect() {
        p2pManager.disconnect()
    }

    override fun onCleared() {
        super.onCleared()
        disconnect()
    }
}
