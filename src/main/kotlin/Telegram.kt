import io.github.reactivecircus.cache4k.Cache
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.io.File

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

@Serializable
data class SetMyCommandsRequest(
    @SerialName("commands")
    val commands: List<BotCommand>
)

@Serializable
data class BotCommand(
    @SerialName("command")
    val command: String,
    @SerialName("description")
    val description: String
)

fun main(args: Array<String>) {

    val botToken = args[0]
    var lastUpdateId = 0L
    val json = Json { ignoreUnknownKeys = true }
    val trainers = Cache.Builder<Long, LearnWordsTrainer>()
        .maximumCacheSize(50)
        .build()


    botCommand(
        json, botToken, listOf(
            BotCommand("start", "Глвное меню"),
        )
    )

    while (true) {
        Thread.sleep(2000)
        val result = runCatching { getUpdates(botToken, lastUpdateId) }
        val responseString = result.getOrNull() ?: continue
        if (responseString != "{\"ok\":true,\"result\":[]}") {
            println(responseString)
            File("src/main/kotlin/Result/log.txt").appendText("$responseString\n")
        }

        if (responseString.contains("Too Many Requests")) continue
        val response: Response = json.decodeFromString(responseString)
        if (response.result.isEmpty()) continue
        val sortedUpdates = response.result.sortedBy { it.updateId }
        sortedUpdates.forEach { handleUpdate(it, json, botToken, trainers) }
        lastUpdateId = sortedUpdates.last().updateId + 1
    }
}

fun handleUpdate(update: Update, json: Json, botToken: String, trainers: Cache<Long, LearnWordsTrainer>) {

    val message = update.message?.text
    val chatId = update.message?.chat?.id ?: update.callbackQuery?.message?.chat?.id ?: return
    val data = update.callbackQuery?.data
    val trainer = trainers.get(chatId) ?: run {
        val newTrainer = LearnWordsTrainer("src/main/kotlin/Result/$chatId.txt")
        trainers.put(chatId, newTrainer)
        newTrainer
    }
    if (message?.lowercase() == MAIN_MENU || data == MAIN_MENU) {
        sendMenu(json, botToken, chatId)
    }

    if (data == STATISTICS_CLICKED) {
        val statistics = trainer.getStatistics()
        sendResetButton(
            json,
            botToken, chatId,
            "Выучено ${statistics.countLearnedWords} из " +
                    "${statistics.countAllWords} слов | " +
                    "${statistics.learnedPercent}%\n\n" +
                    "Слово считаеться выученным если на него ${trainer.learnedAnswerCount} раза правильно ответили.\n" +
                    "Если в разделе останеться меньше ${trainer.countOfQuestionWords}-х не выученных слов,\n" +
                    "то среди вариантов будут повторяться уже выученные."
        )
    }

    if (data == RESET_CLICKED) {
        trainer.resetProgress()
        sendMessage(json, botToken, chatId, "Прогресс сброшен")
    }

    if (data == LEARN_WORDS_CLICKED) {
        sendListOfSteps(json, botToken, chatId)
    }

    if (data?.startsWith(STEP) == true) {
        val step = data.substringAfter(STEP).toInt()
        checkNextQuestionAndSend(json, trainer, botToken, chatId, step)
    }

    if (data?.startsWith(CALLBACK_DATA_ANSWER_PREFIX) == true) {
        val answerId = data.substringAfter(CALLBACK_DATA_ANSWER_PREFIX).toInt()
        if (trainer.checkAnswer(answerId)) {
            sendMessage(json, botToken, chatId, "Правильно!")
        } else {
            if (trainer.question?.correctAnswer?.original != null) {
                sendMessage(
                    json,
                    botToken,
                    chatId,
                    "Не правильно: ${trainer.question?.correctAnswer?.original} - " +
                            "${trainer.question?.correctAnswer?.translate}"
                )
            } else sendMenu(json, botToken, chatId)
        }
        val step = trainer.question?.correctAnswer?.groupAlphabet
        if (step != null) {
            checkNextQuestionAndSend(json, trainer, botToken, chatId, step)
        }
    }
}

fun checkNextQuestionAndSend(json: Json, trainer: LearnWordsTrainer, botToken: String, chatId: Long, step: Int) {
    val question = trainer.getNextQuestion(step)
    if (question == null) {
        sendMessage(json, botToken, chatId, "Вы выучили все словав этом разделе")
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
            question.variants.mapIndexed { index, word ->
                listOf(
                    InlineKeyboard(text = word.translate, callbackData = "$CALLBACK_DATA_ANSWER_PREFIX$index")
                )
            }
        ),
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

fun sendListOfSteps(json: Json, botToken: String, chatId: Long): String {
    val sendMessage = "https://api.telegram.org/bot$botToken/sendMessage"
    val requestBody = SendMessageRequest(
        chatId = chatId,
        text = "Список этапов со страницами из учебника",
        replyMarkup = ReplyMarkup(
            listOf(
                listOf(InlineKeyboard(callbackData = "step_5", text = "Алфавит (страница 16)")),
                listOf(InlineKeyboard(callbackData = "step_1", text = "Слова со страницы 6")),
                listOf(InlineKeyboard(callbackData = "step_2", text = "Слова со страницы 8")),
                listOf(InlineKeyboard(callbackData = "step_3", text = "Слова со страницы 10")),
                listOf(InlineKeyboard(callbackData = "step_4", text = "Слова со страниц 12-15")),
                listOf(InlineKeyboard(callbackData = "step_6", text = "Слова со страницы 22")),
                listOf(InlineKeyboard(callbackData = "step_7", text = "Цвета (страница 24)")),
                listOf(InlineKeyboard(callbackData = "step_8", text = "Module 1. Unit 1 My Home!")),
                listOf(InlineKeyboard(callbackData = "step_9", text = "Unit 2/3")),
                listOf(InlineKeyboard(callbackData = "step_10", text = "Module 2 Unit 4 Numbers")),
                listOf(InlineKeyboard(callbackData = "step_11", text = "Module 2 Unit 4")),
                listOf(InlineKeyboard(callbackData = "step_12", text = "Unit 5-6")),
                listOf(InlineKeyboard(callbackData = "step_13", text = "Module 3 unit 7-9")),
                listOf(InlineKeyboard(callbackData = "step_14", text = "Module 4  Unit 10")),
                listOf(InlineKeyboard(callbackData = "step_15", text = "Unit 11-12")),
                listOf(InlineKeyboard(callbackData = "step_16", text = "Module 5 Unit 13")),
                listOf(InlineKeyboard(callbackData = "step_17", text = "Unit 14-15")),
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

fun sendResetButton(json: Json, botToken: String, chatId: Long, message: String): String {
    val sendMessage = "https://api.telegram.org/bot$botToken/sendMessage"
    val requestBody = SendMessageRequest(
        chatId = chatId,
        text = message,
        replyMarkup = ReplyMarkup(
            listOf(
                listOf(
                    InlineKeyboard(callbackData = RESET_CLICKED, text = "Сбросить прогресс")
                ),
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

fun botCommand(json: Json, botTokenTg: String, command: List<BotCommand>) {
    val setMyCommandsRequest = SetMyCommandsRequest(command)
    val requestBody = json.encodeToString(setMyCommandsRequest)
    val client = OkHttpClient()
    val request = Request.Builder()
        .url("https://api.telegram.org/bot$botTokenTg/setMyCommands")
        .post(requestBody.toRequestBody("application/json".toMediaTypeOrNull()))
        .build()
    val response = client.newCall(request).execute()
    val responseBody = response.body?.string()
    println(responseBody)
    responseBody?.let {
        ""
    }
    response.close()
}

const val STATISTICS_CLICKED = "statistics_clicked"
const val LEARN_WORDS_CLICKED = "learn_words_clicked"
const val CALLBACK_DATA_ANSWER_PREFIX = "answer_"
const val RESET_CLICKED = "reset_clicked"
const val MAIN_MENU = "/start"
const val STEP = "step_"