fun main() {

    val responseString = """
        {
          "ok": true,
          "result": [
            {
              "update_id": 512141866,
              "message": {
                "message_id": 230,
                "from": {
                  "id": 1081959967,
                  "is_bot": false,
                  "first_name": "COBa",
                  "username": "KOTLETAu3COBbl",
                  "language_code": "ru"
                },
                "chat": {
                  "id": 1081959967,
                  "first_name": "COBa",
                  "username": "KOTLETAu3COBbl",
                  "type": "private"
                },
                "date": 1683895595,
                "text": "/start",
                "entities": [
                  {
                    "offset": 0,
                    "length": 6,
                    "type": "bot_command"
                  }
                ]
              }
            }
          ]
        }
    """.trimIndent()

//    val word = Json.encodeToString(
//        Word(
//            original ="Hello",
//            translate = "Привет",
//            correctAnswersCount = 0,
//        )
//    )
//    println(word)
//
//    val wordObject = Json.decodeFromString<Word>(
//        """{"original":"Hello","translate":"Привет"}"""
//    )
//    println(wordObject)

//    val response = json.decodeFromString<Response>(responseString)
//    println(response)

}