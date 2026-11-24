package com.example.assistente

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Conversa com a API do Gemini (Google). Chame sempre fora da thread principal. */
object AiClient {

    private const val MODELO = "gemini-flash-latest"

    private fun sistema(): String {
        val agora = SimpleDateFormat("EEEE, dd/MM/yyyy HH:mm", Locale("pt", "BR")).format(Date())
        return """
Você é o cérebro de um assistente de celular Android. Agora é: $agora.
Converta o pedido do usuário em ações. Responda SOMENTE com JSON válido, sem texto extra, neste formato:
{"fala":"resposta curta e amigável em português","acoes":[ ... ]}

Tipos de ação disponíveis:
{"tipo":"alarme","hora":0-23,"minuto":0-59,"texto":"rótulo","dias":[1-7]}  (dias opcional; 1=domingo ... 7=sábado; sem dias = toca uma vez)
{"tipo":"timer","segundos":N,"texto":"rótulo"}
{"tipo":"sms","numero":"opcional","texto":"mensagem"}
{"tipo":"whatsapp","numero":"só dígitos com DDI, opcional","texto":"mensagem"}
{"tipo":"ligar","numero":"número"}
{"tipo":"abrir_app","app":"nome do app"}
{"tipo":"print"}
{"tipo":"lanterna","ligar":true/false}
{"tipo":"configuracoes","tela":"wifi|bluetooth|som|tela|bateria|geral"}
{"tipo":"pesquisar","busca":"termo"}

Regras: "me acorda amanhã às 6" = alarme hora 6 minuto 0. Se faltar informação essencial, devolva "acoes":[] e peça na "fala".
Nunca invente números de telefone. Se o usuário não deu número, omita o campo.
""".trimIndent()
    }

    fun interpretar(apiKey: String, pedido: String): String {
        val corpo = JSONObject()
            .put("system_instruction", JSONObject().put("parts",
                JSONArray().put(JSONObject().put("text", sistema()))))
            .put("contents", JSONArray().put(JSONObject()
                .put("role", "user")
                .put("parts", JSONArray().put(JSONObject().put("text", pedido)))))
            .put("generationConfig", JSONObject().put("responseMimeType", "application/json"))

        val url = "https://generativelanguage.googleapis.com/v1beta/models/$MODELO:generateContent"
        val con = URL(url).openConnection() as HttpURLConnection
        try {
            con.requestMethod = "POST"
            con.connectTimeout = 15000
            con.readTimeout = 30000
            con.doOutput = true
            con.setRequestProperty("content-type", "application/json")
            con.setRequestProperty("x-goog-api-key", apiKey)
            con.outputStream.use { it.write(corpo.toString().toByteArray()) }

            val codigo = con.responseCode
            val stream = if (codigo in 200..299) con.inputStream else con.errorStream
            val texto = stream.bufferedReader().use { it.readText() }
            if (codigo !in 200..299) throw RuntimeException("API $codigo: $texto")

            val partes = JSONObject(texto)
                .getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content").getJSONArray("parts")
            return buildString {
                for (i in 0 until partes.length()) append(partes.getJSONObject(i).optString("text"))
            }
        } finally {
            con.disconnect()
        }
    }
}
