package com.example.quickpavlo.domain.usecase

import com.example.quickpavlo.domain.repository.ChatRepository

// Caso de uso para enviar mensajes de chat
class SendMessageUseCase(
    private val chatRepository: ChatRepository
) {
    // Ejecución de la lógica de negocio
}
