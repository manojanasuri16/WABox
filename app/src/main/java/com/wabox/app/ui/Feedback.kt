package com.wabox.app.ui

import com.wabox.app.core.model.AddSourceResult
import com.wabox.app.core.model.AppKind
import com.wabox.app.core.model.SaveResult
import com.wabox.app.core.util.Formats
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

enum class MessageTone { SUCCESS, INFO, ERROR }

data class UserMessage(val text: String, val tone: MessageTone = MessageTone.INFO, val id: Long = System.nanoTime())

/** App-wide transient feedback, rendered by the ToastHost at the root of the UI. */
class UserMessages {
    private val _messages = MutableSharedFlow<UserMessage>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val messages: SharedFlow<UserMessage> = _messages.asSharedFlow()
    fun post(message: UserMessage) { _messages.tryEmit(message) }
    fun post(text: String, tone: MessageTone = MessageTone.INFO) = post(UserMessage(text, tone))
}

fun feedbackText(result: SaveResult): String = when (result) {
    is SaveResult.Saved -> "Saved to ${result.location}"
    SaveResult.AlreadySaved -> "Already saved"
    SaveResult.SourceMissing -> "This status is no longer on your device"
    is SaveResult.NoSpace -> "Not enough storage (${Formats.size(result.needed)} needed)"
    is SaveResult.Failed -> "Couldn't save: ${result.reason}"
}

fun feedbackTone(result: SaveResult): MessageTone = when (result) {
    is SaveResult.Saved -> MessageTone.SUCCESS
    SaveResult.AlreadySaved -> MessageTone.INFO
    else -> MessageTone.ERROR
}

fun appName(kind: AppKind): String = when (kind) {
    AppKind.WHATSAPP -> "WhatsApp"
    AppKind.BUSINESS -> "WhatsApp Business"
    AppKind.OTHER -> "Folder"
}

fun feedbackText(result: AddSourceResult): UserMessage = when (result) {
    is AddSourceResult.Added -> when (result.folderCount) {
        0 -> UserMessage("${appName(result.appKind)} connected. No statuses folder yet. View a status in the app first.", MessageTone.INFO)
        1 -> UserMessage("${appName(result.appKind)} connected", MessageTone.SUCCESS)
        else -> UserMessage("${appName(result.appKind)} connected · ${result.folderCount} status folders found", MessageTone.SUCCESS)
    }
    is AddSourceResult.Reconnected -> UserMessage("${appName(result.appKind)} reconnected", MessageTone.SUCCESS)
    is AddSourceResult.AlreadyConnected -> UserMessage("${appName(result.appKind)} is already connected", MessageTone.INFO)
    AddSourceResult.NotAStatusFolder -> UserMessage("That folder has no WhatsApp statuses. Choose Android › media › com.whatsapp.", MessageTone.ERROR)
    is AddSourceResult.Failed -> UserMessage(result.reason, MessageTone.ERROR)
}
