import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

@Serializable
data class Update(
    @SerialName("update_id")
    val updateId: Long,
    @SerialName("message")
    val message: Message? = null,
    @SerialName("callback_query")
    val callbackQuery: CallbackQuery? = null,
)

@Serializable
data class Response(
    @SerialName("result")
    val result: List<Update>,
)

@Serializable
data class Message(
    @SerialName("text")
    val text: String,
    @SerialName("chat")
    val chat: Chat,
)

@Serializable
data class CallbackQuery(
    @SerialName("data")
    val data: String? = null,
    @SerialName("message")
    val message: Message? = null,
)

@Serializable
data class Chat(
    @SerialName("id")
    val id: Long,
)

@Serializable
data class SendMessageRequest(
    @SerialName("chat_id")
    val chatId: Long?,
    @SerialName("text")
    val text: String,
    @SerialName("reply_markup")
    val replyMarkup: ReplyMarkup? = null,
)

@Serializable
data class ReplyMarkup(
    @SerialName("inline_keyboard")
    val inlineKeyboard: List<List<InlineKeyboard>>,
)

@Serializable
data class InlineKeyboard(
    @SerialName("callback_data")
    val callbackData: String,
    @SerialName("text")
    val text: String,
)

fun main(args: Array<String>) {

    val botToken = args[0]
    var lastUpdateId = 0L
    val json = Json { ignoreUnknownKeys = true }
    val trainers = HashMap<Long, LearnWordsTrainer>()

    while (true) {
        Thread.sleep(2000)
        val responseString = getUpdates(botToken, lastUpdateId)
        println(responseString)

        val response: Response = json.decodeFromString(responseString)
        if (response.result.isEmpty()) continue
        val sortedUpdates = response.result.sortedBy { it.updateId }
        sortedUpdates.forEach { handleUpdate(it, json, botToken, trainers) }
        lastUpdateId = sortedUpdates.last().updateId + 1
    }
}

fun handleUpdate(update: Update, json: Json, botToken: String, trainers: HashMap<Long, LearnWordsTrainer>) {

    val message = update.message?.text
    val chatId = update.message?.chat?.id ?: update.callbackQuery?.message?.chat?.id ?: return
    val data = update.callbackQuery?.data
    val trainer = trainers.getOrPut(chatId) { LearnWordsTrainer("$chatId.txt") }

    if (message?.lowercase() == MAIN_MENU || data == MAIN_MENU) {
        sendMenu(json, botToken, chatId)
    }

    if (data == STATISTICS_CLICKED) {
        val statistics = trainer.getStatistics()
        sendMessage(
            json,
            botToken, chatId,
            "Выучено ${statistics.countLearnedWords} из " +
                    "${statistics.countAllWords} слов | " +
                    "${statistics.learnedPercent}%"
        )
    }

    if (data == RESET_CLICKED) {
        trainer.resetProgress()
        sendMessage(json, botToken, chatId, "Прогресс сброшен")
    }

    if (data == LEARN_WORDS_CLICKED) {
        checkNextQuestionAndSend(json, trainer, botToken, chatId)
    }

    if (data?.startsWith(CALLBACK_DATA_ANSWER_PREFIX) == true) {
        val answerId = data.substringAfter(CALLBACK_DATA_ANSWER_PREFIX).toInt()
        if (trainer.checkAnswer(answerId)) {
            sendMessage(json, botToken, chatId, "Правильно")
        } else {
            sendMessage(
                json,
                botToken,
                chatId,
                "Не правильно: ${trainer.question?.correctAnswer?.original} - " +
                        "${trainer.question?.correctAnswer?.translate}"
            )
        }
        checkNextQuestionAndSend(json, trainer, botToken, chatId)
    }
}

fun checkNextQuestionAndSend(json: Json, trainer: LearnWordsTrainer, botToken: String, chatId: Long) {
    val question = trainer.getNextQuestion()
    if (question == null) {
        sendMessage(json, botToken, chatId, "Вы выучили все слова")
        sendMenu(json, botToken, chatId)
    } else {
        sendQuestion(json, botToken, chatId, question)
    }
}

fun getUpdates(botToken: String, updateId: Long): String {
    val urlGetUpdates = "https://api.telegram.org/bot$botToken/getUpdates?offset=$updateId"
    val client: HttpClient = HttpClient.newBuilder().build()
    val request: HttpRequest = HttpRequest.newBuilder().uri(URI.create(urlGetUpdates)).build()
    val response: HttpResponse<String> = client.send(request, HttpResponse.BodyHandlers.ofString())
    return response.body()
}

fun sendMessage(json: Json, botToken: String, chatId: Long, message: String): String {
    val sendMessage = "https://api.telegram.org/bot$botToken/sendMessage"
    val requestBody = SendMessageRequest(
        chatId = chatId,
        text = message,
    )
    val requestBodyString = json.encodeToString(requestBody)
    val client: HttpClient = HttpClient.newBuilder().build()
    val request: HttpRequest = HttpRequest.newBuilder().uri(URI.create(sendMessage))
        .header("Content-type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(requestBodyString))
        .build()
    val response: HttpResponse<String> = client.send(request, HttpResponse.BodyHandlers.ofString())
    return response.body()
}

fun sendQuestion(json: Json, botToken: String, chatId: Long, question: Question): String {
    val sendMessage = "https://api.telegram.org/bot$botToken/sendMessage"
    val requestBody = SendMessageRequest(
        chatId = chatId,
        text = question.correctAnswer.original,
        replyMarkup = ReplyMarkup(
            listOf(
                listOf(
                    InlineKeyboard(
                        text = question.variants[0].translate,
                        callbackData = "${CALLBACK_DATA_ANSWER_PREFIX}0"
                    )
                ),
                listOf(
                    InlineKeyboard(
                        text = question.variants[1].translate,
                        callbackData = "${CALLBACK_DATA_ANSWER_PREFIX}1"
                    )
                ),
                listOf(
                    InlineKeyboard(
                        text = question.variants[2].translate,
                        callbackData = "${CALLBACK_DATA_ANSWER_PREFIX}2"
                    )
                ),
                listOf(
                    InlineKeyboard(
                        text = question.variants[3].translate,
                        callbackData = "${CALLBACK_DATA_ANSWER_PREFIX}3"
                    )
                ),
                listOf(
                    InlineKeyboard(
                        callbackData = MAIN_MENU, text = "Возврат в главное меню"
                    )
                )
            )
        )
//        replyMarkup = ReplyMarkup(
//           question.variants.mapIndexed { index, word ->
//                listOf(
//                    InlineKeyboard(
//                        text = word.translate, callbackData = "$CALLBACK_DATA_ANSWER_PREFIX$index"
//                    )
//               )
//            }
    )
    val requestBodyString = json.encodeToString(requestBody)
    val client: HttpClient = HttpClient.newBuilder().build()
    val request: HttpRequest = HttpRequest.newBuilder().uri(URI.create(sendMessage))
        .header("Content-type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(requestBodyString))
        .build()
    val response: HttpResponse<String> = client.send(request, HttpResponse.BodyHandlers.ofString())
    return response.body()
}

fun sendMenu(json: Json, botToken: String, chatId: Long): String {
    val sendMessage = "https://api.telegram.org/bot$botToken/sendMessage"
    val requestBody = SendMessageRequest(
        chatId = chatId,
        text = "Основное меню",
        replyMarkup = ReplyMarkup(
            listOf(
                listOf(
                    InlineKeyboard(callbackData = LEARN_WORDS_CLICKED, text = "Изучать слова"),
                    InlineKeyboard(callbackData = STATISTICS_CLICKED, text = "Статистика"),
                ),
                listOf(
                    InlineKeyboard(callbackData = RESET_CLICKED, text = "Сбросить прогресс")
                )
            )
        )
    )
    val requestBodyString = json.encodeToString(requestBody)
    val client: HttpClient = HttpClient.newBuilder().build()
    val request: HttpRequest = HttpRequest.newBuilder().uri(URI.create(sendMessage))
        .header("Content-type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(requestBodyString))
        .build()
    val response: HttpResponse<String> = client.send(request, HttpResponse.BodyHandlers.ofString())
    return response.body()
}

fun sendMenuButton(json: Json, botToken: String, chatId: Long): String {
    val sendMessage = "https://api.telegram.org/bot$botToken/sendMessage"
    val requestBody = SendMessageRequest(
        chatId = chatId,
        text = "Возврат в главное меню",
        replyMarkup = ReplyMarkup(
            listOf(
                listOf(
                    InlineKeyboard(callbackData = MAIN_MENU, text = "Возврат в главное меню"),
                )
            )
        )
    )
    val requestBodyString = json.encodeToString(requestBody)
    val client: HttpClient = HttpClient.newBuilder().build()
    val request: HttpRequest = HttpRequest.newBuilder().uri(URI.create(sendMessage))
        .header("Content-type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(requestBodyString))
        .build()
    val response: HttpResponse<String> = client.send(request, HttpResponse.BodyHandlers.ofString())
    return response.body()
}

const val STATISTICS_CLICKED = "statistics_clicked"
const val LEARN_WORDS_CLICKED = "learn_words_clicked"
const val CALLBACK_DATA_ANSWER_PREFIX = "answer_"
const val RESET_CLICKED = "reset_clicked"
const val MAIN_MENU = "/start"