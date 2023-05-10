import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets

fun main(args: Array<String>) {

    val botToken = args[0]
    val telegramBot = TelegramBotService()
    var updateId = 0

    val updatesIdRegex = "\"update_id\":(\\d+)".toRegex()
    val messageTextRegex = "\"text\":\"(.+?)\"".toRegex()
    val chatIdRegex = "\"chat\":\\{\"id\":(\\d+)".toRegex()
    val dataRegex = "\"data\":\"(.+?)\"".toRegex()

    val trainer: LearnWordsTrainer = LearnWordsTrainer()

    while (true) {
        Thread.sleep(2000)
        val updates = telegramBot.getUpdates(botToken, updateId)
        println(updates)
        updateId = updatesIdRegex.find(updates)?.groups?.get(1)?.value?.toIntOrNull()?.plus(1) ?: continue

        val message = messageTextRegex.find(updates)?.groups?.get(1)?.value
        val chatId = chatIdRegex.find(updates)?.groups?.get(1)?.value?.toInt()
        val data = dataRegex.find(updates)?.groups?.get(1)?.value

        if (message?.lowercase() == "/start" && chatId != null) {
            telegramBot.sendMenu(botToken, chatId)
        }
        if (data?.lowercase() == STATISTICS_CLICKED && chatId != null) {
            val statistics = trainer.getStatistics()
            telegramBot.sendMessage(
                botToken, chatId,
                "Выучено ${statistics.countLearnedWords} из " +
                        "${statistics.countAllWords} слов | " +
                        "${statistics.learnedPercent}%"
            )
        }
        if (data?.lowercase() == LEARN_WORDS_CLICKED && chatId != null) {
            val question = trainer.getNextQuestion()
            if (question == null) {
                println("Вы выучили все слова в базе")
            } else {
                telegramBot.sendQuestion(botToken, chatId, question)
            }
        }
        if (data?.startsWith(CALLBACK_DATA_ANSWER_PREFIX) == true && chatId != null) {
            val answer = data.substringAfter(CALLBACK_DATA_ANSWER_PREFIX).toInt()
            if (trainer.checkAnswer(answer)) {
                telegramBot.sendMessage(botToken, chatId, "Правильно")
            } else {
                telegramBot.sendMessage(
                    botToken,
                    chatId,
                    "Не правильно: ${trainer.question?.correctAnswer?.original} - " +
                            "${trainer.question?.correctAnswer?.translate}"
                )
            }
            telegramBot.checkNextQuestionAndSend(trainer, botToken, chatId)
        }
    }
}

class TelegramBotService {

    fun checkNextQuestionAndSend(trainer: LearnWordsTrainer, botToken: String, chatId: Int) {
        val question = trainer.getNextQuestion()
        if (question == null) {
            println("Вы выучили все слова в базе")
        } else {
            sendQuestion(botToken, chatId, question)
        }
    }

    fun getUpdates(botToken: String, updateId: Int): String {
        val urlGetUpdates = "https://api.telegram.org/bot$botToken/getUpdates?offset=$updateId"
        val client: HttpClient = HttpClient.newBuilder().build()
        val request: HttpRequest = HttpRequest.newBuilder().uri(URI.create(urlGetUpdates)).build()
        val response: HttpResponse<String> = client.send(request, HttpResponse.BodyHandlers.ofString())
        return response.body()
    }

    fun sendMessage(botToken: String, chatId: Int, message: String): String {
        val encoded = URLEncoder.encode(
            message,
            StandardCharsets.UTF_8
        )
        val sendMessage = "https://api.telegram.org/bot$botToken/sendMessage?chat_id=$chatId&text=$encoded"
        val client: HttpClient = HttpClient.newBuilder().build()
        val request: HttpRequest = HttpRequest.newBuilder().uri(URI.create(sendMessage)).build()
        val response: HttpResponse<String> = client.send(request, HttpResponse.BodyHandlers.ofString())
        return response.body()
    }

    fun sendQuestion(botToken: String, chatId: Int, question: Question): String {
        val correctAnswer = question.correctAnswer.original
        val encoded = URLEncoder.encode(
            correctAnswer,
            StandardCharsets.UTF_8
        )
        val sendMessage = "https://api.telegram.org/bot$botToken/sendMessage?chat_id=$chatId&text=$encoded"
        val sendMenuBody = """
            {
                "chat_id": $chatId,
                "text": "Выберите перевод слова",
                "reply_markup": {
                "inline_keyboard": [
                    [
                        ${
            question.variants.mapIndexed { index, variant ->
                """{
                            "text": "${variant.translate}",
                            "callback_data": "$CALLBACK_DATA_ANSWER_PREFIX${index}"
                            }
                    
                        """
            }.joinToString(separator = ",\n")
        }
                    ]
                ]
                }
             }
        """.trimIndent()
        val client: HttpClient = HttpClient.newBuilder().build()
        val request: HttpRequest = HttpRequest.newBuilder().uri(URI.create(sendMessage))
            .header("Content-type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(sendMenuBody))
            .build()
        val response: HttpResponse<String> = client.send(request, HttpResponse.BodyHandlers.ofString())
        return response.body()
    }

    fun sendMenu(botToken: String, chatId: Int): String {
        val sendMessage = "https://api.telegram.org/bot$botToken/sendMessage"
        val sendMenuBody = """
            {
                "chat_id": $chatId,
                "text": "Главное меню",
                "reply_markup": {
                    "inline_keyboard": [
                        [
                            {
                                "text": "Изучить слова",
                                "callback_data": "$LEARN_WORDS_CLICKED"
                            },
                            {
                                "text": "Статистика",
                                "callback_data": "$STATISTICS_CLICKED"
                            }
                        ]
                    ]
                }
            }            
        """.trimIndent()
        val client: HttpClient = HttpClient.newBuilder().build()
        val request: HttpRequest = HttpRequest.newBuilder().uri(URI.create(sendMessage))
            .header("Content-type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(sendMenuBody))
            .build()
        val response: HttpResponse<String> = client.send(request, HttpResponse.BodyHandlers.ofString())
        return response.body()
    }
}

const val STATISTICS_CLICKED = "statistics_clicked"
const val LEARN_WORDS_CLICKED = "learn_words_clicked"
const val CALLBACK_DATA_ANSWER_PREFIX = "answer_"