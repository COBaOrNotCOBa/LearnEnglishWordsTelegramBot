import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

fun main(args: Array<String>) {

    val botToken = args[0]
    val telegramBot = TelegramBotService()
    var updateId = 0
    val messageText = "\"text\":\"(.+?)\""
    val messageChatId = "\"chat\":\\{\"id\":(.+?),"
    val messageUpdates = "\"update_id\":(.+?),"

    while (true) {
        Thread.sleep(2000)
        val updates: String = telegramBot.getUpdates(botToken, updateId)
        val text = telegramBot.regex(messageText, updates) ?: continue
        val chatId = telegramBot.regex(messageChatId, updates) ?: continue
        updateId = telegramBot.regex(messageUpdates, updates)?.toInt()?.plus(1) ?: continue

        if (text == "Hello") telegramBot.sendMessage(botToken, chatId, text)
    }
}

class TelegramBotService {
    fun regex(searchingText: String, updates: String): String? {
        val messageRegex: Regex = searchingText.toRegex()
        val matchResult: MatchResult? = messageRegex.find(updates)
        val group = matchResult?.groups
        return group?.get(1)?.value
    }

    fun getUpdates(botToken: String, updateId: Int): String {
        val urlGetUpdates = "https://api.telegram.org/bot$botToken/getUpdates?offset=$updateId"
        val client: HttpClient = HttpClient.newBuilder().build()
        val request: HttpRequest = HttpRequest.newBuilder().uri(URI.create(urlGetUpdates)).build()
        val response: HttpResponse<String> = client.send(request, HttpResponse.BodyHandlers.ofString())

        return response.body()
    }

    fun sendMessage(botToken: String, chatId: String?, text: String?): String {
        val urlChatId = "https://api.telegram.org/bot$botToken/sendMessage?chat_id=$chatId&text=$text"
        val client: HttpClient = HttpClient.newBuilder().build()
        val request: HttpRequest = HttpRequest.newBuilder().uri(URI.create(urlChatId)).build()
        val response: HttpResponse<String> = client.send(request, HttpResponse.BodyHandlers.ofString())

        return response.body()
    }
}