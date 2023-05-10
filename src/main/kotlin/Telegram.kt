import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse


fun main(args: Array<String>) {

    val botToken = args[0]
    var updateId = 0
    val messageText = "\"text\":\"(.+?)\""
    val messageChatId = "\"chat\":\\{\"id\":(.+?),"
    val messageUpdates = "\"update_id\":(.+?),"

    while (true) {
        Thread.sleep(2000)
        val updates: String = getUpdates(botToken, updateId)
        println(updates)

        val text = regex(messageText, updates) ?: continue
        println(text)
        val chatId = regex(messageChatId, updates) ?: continue
        println(chatId)
        updateId = regex(messageUpdates, updates)?.toInt()?.plus(1) ?: continue
        println(updateId)

//        val messageTextRegex: Regex = "\"text\":\"(.+?)\"".toRegex()
//        val matchResult: MatchResult? = messageTextRegex.find(updates)
//        val group = matchResult?.groups
//        val text = group?.get(1)?.value
//
//        val chatIdRegex: Regex = "\"chat\":\\{\"id\":(.+?),".toRegex()
//        val chatIdMatchResult: MatchResult? = chatIdRegex.find(updates)
//        val chatIdGroup = chatIdMatchResult?.groups
//        val chatIdNumber = chatIdGroup?.get(1)?.value

//        val updateIdRegex: Regex = "\"update_id\":(.+?),".toRegex()
//        val matchResultId: MatchResult? = updateIdRegex.find(updates)
//        val groupId = matchResultId?.groups
//        val updateIdString = groupId?.get(1)?.value ?: continue
//        updateId = updateIdString.toInt() + 1

//        sendMessage(botToken, chatIdNumber, text)
    }
}

fun regex(serchingText: String, updates: String): String? {
    val messageRegex: Regex = serchingText.toRegex()
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
    val urlChatId = "https://api.telegram.org/bot$botToken/sendMessage?chat_id=$chatId"
    val urlText = "https://api.telegram.org/bot$botToken/sendMessage?text=$text"
    val client: HttpClient = HttpClient.newBuilder().build()
    val request: HttpRequest = HttpRequest.newBuilder().uri(URI.create(urlChatId)).build()
    val response: HttpResponse<String> = client.send(request, HttpResponse.BodyHandlers.ofString())

    return response.body()
}