import org.slf4j.LoggerFactory
import io.github.reactivecircus.cache4k.Cache
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.io.File


@Serializable
data class Response(
    @SerialName("result")
    val result: List<Update>,
)

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
data class Message(
    @SerialName("text")
    val text: String? = null,
    @SerialName("chat")
    val chat: Chat,
    @SerialName("voice")
    val voice: Voice? = null,
)

@Serializable
data class Voice(
    @SerialName("file_id")
    val fileId: String? = null,
    @SerialName("file_unique_id")
    val file_unique_id: String? = null,
    @SerialName("file_path")
    val file_path: String? = null,
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
    @SerialName("audio")
    val audio: String? = null
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

@Serializable
data class AudioResponse(
    @SerialName("result")
    val result: Audio?
)

@Serializable
data class Audio(
    @SerialName("file_id")
    val fileId: String,
    @SerialName("file_unique_id")
    val file_unique_id: String,
    @SerialName("file_path")
    val file_path: String? = null,
)

@Serializable
data class SendAudioResponse(
    @SerialName("result")
    val result: SendAudioResult
)

@Serializable
data class SendAudioResult(
    @SerialName("voice")
    val voice: SendAudio
)

@Serializable
data class SendAudio(
    @SerialName("file_id")
    val fileId: String,
    @SerialName("file_unique_id")
    val file_unique_id: String,
    @SerialName("file_path")
    val file_path: String? = null,
)

fun main(args: Array<String>) {

    val botToken = args[0]
    var lastUpdateId = 0L
    val json = Json { ignoreUnknownKeys = true }
    val trainers = Cache.Builder<Long, LearnWordsTrainer>()
        .maximumCacheSize(100)
        .build()
    val logger = LoggerFactory.getLogger("Log")
    val savingVoice = mutableMapOf<Long, List<String>>()

    botCommand(
        json, botToken, listOf(
            BotCommand("start", "Главное меню"),
        )
    )

    while (true) {
        Thread.sleep(2500)
        val result = runCatching { getUpdates(botToken, lastUpdateId) }
        val responseString = result.getOrNull() ?: continue
        if (responseString != "{\"ok\":true,\"result\":[]}") {
            println(responseString)
            logger.info(responseString)
        }

        if (responseString.contains("error_code")) {
            Thread.sleep(5000)
            continue
        }

        val response: Response = json.decodeFromString(responseString)
        if (response.result.isEmpty()) continue
        val sortedUpdates = response.result.sortedBy { it.updateId }
        sortedUpdates.forEach { handleUpdate(it, json, botToken, trainers, savingVoice) }
        lastUpdateId = sortedUpdates.last().updateId + 1
    }
}

fun handleUpdate(
    update: Update,
    json: Json,
    botToken: String,
    trainers: Cache<Long, LearnWordsTrainer>,
    savingVoice: MutableMap<Long, List<String>>
) {

    val logger = LoggerFactory.getLogger("Log")

    val message = update.message?.text
    val chatId = update.message?.chat?.id ?: update.callbackQuery?.message?.chat?.id ?: return
    val data = update.callbackQuery?.data
    val trainer = trainers.get(chatId) ?: run {
        val newTrainer = LearnWordsTrainer("src/main/kotlin/Result/$chatId.txt")
        trainers.put(chatId, newTrainer)
        newTrainer
    }

    if (message?.lowercase() == MAIN_MENU || data == MAIN_MENU) {
        if (chatId == 2090279521L) {
            sendAdminMenu(json, botToken, chatId)
        } else sendMenu(json, botToken, chatId)
    }

    if (chatId in listOf(2090279521L)) {

        if (savingVoice[chatId] != listOf("", "") && savingVoice[chatId] != null) {
            val voice = update.message?.voice
            if (voice != null) {
                val listOfWords = trainer.getListOfWordsForAudioRecord(savingVoice[chatId]?.get(0)?.toInt() ?: 0)
                var word = listOfWords[savingVoice[chatId]?.get(1)?.toInt() ?: 0].original
                if (word.contains('?')) word = word.replace("?", "")
                listOfWords[savingVoice[chatId]?.get(1)?.toInt() ?: 0].audio = word
                trainer.saveAudio()
                downloadAudio(json, botToken, voice.fileId, word)
            }
            savingVoice[chatId] = listOf("", "")
        }

        if (data == ADD_VOICE) {
            sendListOfStepsForVoice(json, botToken, chatId)
        }

        if (data?.startsWith(AUDIO_STEP) == true) {
            val audioStep = data.substringAfter(AUDIO_STEP).toInt()
            sendListOfWordsForRecord(json, trainer, botToken, chatId, audioStep)
        }

        if (data?.startsWith(CALLBACK_DATA_AUDIO_SAVE) == true) {
            savingVoice[chatId] = data.substringAfter(CALLBACK_DATA_AUDIO_SAVE).split("-")
            sendMessage(
                json,
                botToken,
                chatId,
                "Отправите голосовое сообщение с произношением выбранного слова (слов,буквы)"
            )
        }

        if (data?.startsWith(CALLBACK_DATA_AUDIO_PLAY) == true) {
            savingVoice[chatId] = data.substringAfter(CALLBACK_DATA_AUDIO_PLAY).split("-")
            val listOfWords = trainer.getListOfWordsForAudioRecord(savingVoice[chatId]?.get(0)?.toInt() ?: 0)
            var word = listOfWords[savingVoice[chatId]?.get(1)?.toInt() ?: 0].original
            if (word.contains('?')) word = word.replace("?", "")
            if (sendAudio(botToken, chatId, "$AUDIO_PATH$word") == "Ошибка: Файл не существует") {
                logger.info("Ошибка: Файл $AUDIO_PATH$word не существует")
                println("Ошибка: Файл $AUDIO_PATH$word не существует")
                sendMessage(json, botToken, chatId, "Файл не найден, попробуйте перезаписать его")
            }
        }

    }

    if (data == STATISTICS_CLICKED) {
        val statistics = trainer.getStatistics()
        sendResetButton(
            json,
            botToken, chatId,
            "Выучено ${statistics.countLearnedWords} из " +
                    "${statistics.countAllWords} слов | " +
                    "${statistics.learnedPercent}%\n\n" +
                    "Слово считается выученным, если на него ${trainer.learnedAnswerCount} раза правильно ответили.\n" +
                    "Если в разделе останется меньше ${trainer.countOfQuestionWords}-х невыученных слов,\n" +
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

    if (message?.lowercase() == "голос") {
        sendAudio(botToken, chatId, "${AUDIO_PATH}cat")
    }
}

fun checkNextQuestionAndSend(json: Json, trainer: LearnWordsTrainer, botToken: String, chatId: Long, step: Int) {
    val question = trainer.getNextQuestion(step)
    if (question == null) {
        sendMessage(json, botToken, chatId, "Вы выучили все словав этом разделе")
        sendMenu(json, botToken, chatId)
    } else {
        sendQuestionAudio(json, botToken, chatId, question)
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

fun sendAudio(botToken: String, chatId: Long, audioFilePath: String): String {
    val audioFile = File(audioFilePath)
    if (!audioFile.exists()) {
        return "Ошибка: Файл не существует"
    }
    val sendAudioUrl = "https://api.telegram.org/bot$botToken/sendAudio"
    val fileMediaType = "audio/*".toMediaType()
    val requestBody = MultipartBody.Builder()
        .setType(MultipartBody.FORM)
        .addFormDataPart("chat_id", chatId.toString())
        .addFormDataPart("audio", audioFile.name, audioFile.asRequestBody(fileMediaType))
        .build()
    val request = Request.Builder()
        .url(sendAudioUrl)
        .post(requestBody)
        .build()
    val client = OkHttpClient()
    val response = client.newCall(request).execute()

    return response.body?.string() ?: ""
}

fun sendVoice(botToken: String, chatId: Long, audioFilePath: String): String {
    val audioFile = File(audioFilePath)
    if (!audioFile.exists()) {
        return "Ошибка: Файл не существует"
    }
    val sendVoiceUrl = "https://api.telegram.org/bot$botToken/sendVoice"
    val fileMediaType = "audio/*".toMediaType()
    val requestBody = MultipartBody.Builder()
        .setType(MultipartBody.FORM)
        .addFormDataPart("chat_id", chatId.toString())
        .addFormDataPart("voice", audioFile.name, audioFile.asRequestBody(fileMediaType))
        .addFormDataPart("duration", "5")
        .build()
    val request = Request.Builder()
        .url(sendVoiceUrl)
        .post(requestBody)
        .build()
    val client = OkHttpClient()
    val response = client.newCall(request).execute()

    return response.body?.string() ?: ""
}

fun sendQuestionAudio(json: Json, botToken: String, chatId: Long, question: Question): String {
    try {
        val sendAudioUrl = "https://api.telegram.org/bot$botToken/sendVoice"
        val fileMediaType = "audio/*".toMediaType()
        val client = OkHttpClient()
        // Отправка аудио файла
        val audioRequestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("chat_id", chatId.toString())
            .addFormDataPart(
                "voice",
                File("$AUDIO_PATH${question.correctAnswer.audio}").name,
                File("$AUDIO_PATH${question.correctAnswer.audio}").asRequestBody(fileMediaType)
            )
//        .addFormDataPart("duration", "5")
            .build()
        val audioRequest = Request.Builder()
            .url(sendAudioUrl)
            .post(audioRequestBody)
            .build()
        val audioResponse = client.newCall(audioRequest).execute()
        // Получение URL аудио файла
        val audioResponseJson = json.decodeFromString<SendAudioResponse>(audioResponse.body?.string() ?: "")
        val audioUrl = audioResponseJson.result.voice.fileId
        // Отправка вариантов ответов с аудио
        val sendMessageUrl = "https://api.telegram.org/bot$botToken/sendMessage"
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
            audio = audioUrl
        )
        val requestBodyString = json.encodeToString(requestBody)
        val sendMessageRequest = Request.Builder()
            .url(sendMessageUrl)
            .header("Content-type", "application/json")
            .post(requestBodyString.toRequestBody())
            .build()
        val sendMessageResponse = client.newCall(sendMessageRequest).execute()
        return sendMessageResponse.body?.string() ?: ""

    } catch (e: Exception) {
        return sendMenu(json, botToken, chatId)
    }
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

fun sendListOfWordsForRecord(
    json: Json,
    trainer: LearnWordsTrainer,
    botToken: String,
    chatId: Long,
    step: Int
): String {
    val listOfWords = trainer.getListOfWordsForAudioRecord(step)
    val sendMessage = "https://api.telegram.org/bot$botToken/sendMessage"
    val requestBody = SendMessageRequest(
        chatId = chatId,
        text = "Этап номер ${listOfWords[0].groupAlphabet}",
        replyMarkup = ReplyMarkup(
            listOfWords.mapIndexed { index, word ->
                listOf(
                    InlineKeyboard(text = word.original, callbackData = "$CALLBACK_DATA_AUDIO_SAVE$step-$index"),
                    InlineKeyboard(text = word.audio ?: "", callbackData = "$CALLBACK_DATA_AUDIO_PLAY$step-$index")
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

fun downloadAudio(json: Json, botToken: String, fileId: String?, word: String) {
    val client = OkHttpClient()
    val url = "https://api.telegram.org/bot$botToken/getFile?file_id=$fileId"
    val request = Request.Builder()
        .url(url)
        .build()
    val response = client.newCall(request).execute()
    val responseBody = response.body?.string()
    val audio: AudioResponse = json.decodeFromString(responseBody!!)
    val audioUrl = "https://api.telegram.org/file/bot$botToken/${audio.result?.file_path}"
    val audioRequest = Request.Builder()
        .url(audioUrl)
        .build()
    val audioResponse = client.newCall(audioRequest).execute()
    val audioBytes = audioResponse.body?.bytes()
    val saveFile = File("$AUDIO_PATH$word")
    saveFile.writeBytes(audioBytes!!)
}

fun sendListOfSteps(json: Json, botToken: String, chatId: Long): String {
    val sendMessage = "https://api.telegram.org/bot$botToken/sendMessage"
    val requestBody = SendMessageRequest(
        chatId = chatId,
        text = "Список этапов со страницами из учебника",
        replyMarkup = ReplyMarkup(
            listOf(
                listOf(InlineKeyboard(callbackData = "step_1", text = "My Letters! P.6")),
                listOf(InlineKeyboard(callbackData = "step_2", text = "My Letters! P.8")),
                listOf(InlineKeyboard(callbackData = "step_3", text = "My Letters! P.10")),
                listOf(InlineKeyboard(callbackData = "step_4", text = "Letter Blends! P.12-15")),
                listOf(InlineKeyboard(callbackData = "step_5", text = "Big and small! P.16")),
                listOf(InlineKeyboard(callbackData = "step_6", text = "My family! P.22")),
                listOf(InlineKeyboard(callbackData = "step_7", text = "Colours. P.24")),
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

fun sendListOfStepsForVoice(json: Json, botToken: String, chatId: Long): String {
    val sendMessage = "https://api.telegram.org/bot$botToken/sendMessage"
    val requestBody = SendMessageRequest(
        chatId = chatId,
        text = "Список этапов со страницами из учебника для записи звука",
        replyMarkup = ReplyMarkup(
            listOf(
                listOf(InlineKeyboard(callbackData = "audio_step_5", text = "Алфавит (страница 16)")),
                listOf(InlineKeyboard(callbackData = "audio_step_1", text = "Слова со страницы 6")),
                listOf(InlineKeyboard(callbackData = "audio_step_2", text = "Слова со страницы 8")),
                listOf(InlineKeyboard(callbackData = "audio_step_3", text = "Слова со страницы 10")),
                listOf(InlineKeyboard(callbackData = "audio_step_4", text = "Слова со страниц 12-15")),
                listOf(InlineKeyboard(callbackData = "audio_step_6", text = "Слова со страницы 22")),
                listOf(InlineKeyboard(callbackData = "audio_step_7", text = "Цвета (страница 24)")),
                listOf(InlineKeyboard(callbackData = "audio_step_8", text = "Module 1. Unit 1 My Home!")),
                listOf(InlineKeyboard(callbackData = "audio_step_9", text = "Unit 2/3")),
                listOf(InlineKeyboard(callbackData = "audio_step_10", text = "Module 2 Unit 4 Numbers")),
                listOf(InlineKeyboard(callbackData = "audio_step_11", text = "Module 2 Unit 4")),
                listOf(InlineKeyboard(callbackData = "audio_step_12", text = "Unit 5-6")),
                listOf(InlineKeyboard(callbackData = "audio_step_13", text = "Module 3 unit 7-9")),
                listOf(InlineKeyboard(callbackData = "audio_step_14", text = "Module 4  Unit 10")),
                listOf(InlineKeyboard(callbackData = "audio_step_15", text = "Unit 11-12")),
                listOf(InlineKeyboard(callbackData = "audio_step_16", text = "Module 5 Unit 13")),
                listOf(InlineKeyboard(callbackData = "audio_step_17", text = "Unit 14-15")),
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

fun sendAdminMenu(json: Json, botToken: String, chatId: Long): String {
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
                    InlineKeyboard(callbackData = ADD_VOICE, text = "Добавить звуковое произношение")
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
const val AUDIO_PATH = "C:\\Users\\user\\IdeaProjects\\LearnEnglishWordsTelegramBot" +
        "\\src\\main\\kotlin\\Audio\\"
const val ADD_VOICE = "add_voice"
const val AUDIO_STEP = "audio_step_"
const val CALLBACK_DATA_AUDIO_SAVE = "audio_push_save_"
const val CALLBACK_DATA_AUDIO_PLAY = "audio_push_play_"