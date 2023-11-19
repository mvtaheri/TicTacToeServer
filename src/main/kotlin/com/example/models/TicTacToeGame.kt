package com.example.models

import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

class TicTacToeGame {
    private val state = MutableStateFlow(GameState())
    private val playerSocket = ConcurrentHashMap<Char, WebSocketSession>()
    private val gameScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var delayInJob: Job? = null

    init {
        state.onEach(::broadCast).launchIn(gameScope)
    }

    fun connectePlayer(session: WebSocketSession): Char? {
        val isPlayerx = state.value.connectedPlayer.any {
            it == 'x'
        }
        val player = if (isPlayerx) 'o' else 'x'
        state.update {
            if (state.value.connectedPlayer.contains(player)) {
                return null
            }
            if (!playerSocket.contains(player)) {
                playerSocket[player] = session
            }
            it.copy(
                connectedPlayer = it.connectedPlayer + player
            )
        }
        return player
    }

    fun disconnectedPlayer(player: Char) {
        playerSocket.remove(player)
        state.update {
            it.copy(
                connectedPlayer = it.connectedPlayer - player
            )
        }
    }

    suspend fun broadCast(state: GameState) {
        playerSocket.values.forEach { socket ->
            socket.send(
                Json.encodeToString(state)
            )
        }
    }

    fun finishTurn(player: Char, x: Int, y: Int) {
        if (state.value.field[y][x] != null || state.value.winningPlayer != null) {
            return
        }
        if (state.value.playerAtTurn != player) {
            return
        }
        val currentPlayer = state.value.playerAtTurn
        state.update {
            val newField = it.field.also { field ->
                field[y][x] = currentPlayer
            }
            val isBoarFull = newField.all {
                it.all { it != null }
            }
            if (isBoarFull) {
                startNewRoundDelayed()
            }
            it.copy(
                playerAtTurn = if (currentPlayer == 'x') 'o' else 'x',
                field = newField,
                isBoardFull = isBoarFull,
                winningPlayer = getWinningPlayer()?.also {
                    startNewRoundDelayed()
                }
            )
        }
    }

    private fun getWinningPlayer(): Char? {
        val field = state.value.field
        return if (field[0][0] != null && field[0][0] == field[0][1] && field[0][1] == field[0][2]) {
            field[0][0]
        } else if (field[1][0] != null && field[1][0] == field[1][1] && field[1][1] == field[1][2]) {
            field[1][0]
        } else if (field[2][0] != null && field[2][0] == field[2][1] && field[2][1] == field[2][2]) {
            field[2][0]
        } else if (field[0][0] != null && field[0][0] == field[1][0] && field[1][0] == field[2][0]) {
            field[0][0]
        } else if (field[0][1] != null && field[0][1] == field[1][1] && field[1][1] == field[2][1]) {
            field[0][1]
        } else if (field[0][2] != null && field[0][2] == field[1][2] && field[1][2] == field[2][2]) {
            field[0][2]
        } else if (field[0][0] != null && field[0][0] == field[1][1] && field[1][1] == field[2][2]) {
            field[0][0]
        } else if (field[0][2] != null && field[0][2] == field[1][1] && field[1][1] == field[2][0]) {
            field[0][2]
        } else null
    }

    private fun startNewRoundDelayed() {
        delayInJob?.cancel()
        delayInJob = gameScope.launch {
            delay(5000)
            state.update {
                it.copy(
                    playerAtTurn = 'x',
                    field = GameState.emptyField(),
                    winningPlayer = null,
                    isBoardFull = false
                )
            }
        }
    }
}